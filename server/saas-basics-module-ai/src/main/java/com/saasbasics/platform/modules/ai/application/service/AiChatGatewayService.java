package com.saasbasics.platform.modules.ai.application.service;

import com.saasbasics.platform.common.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.api.AiGatewayFacade;
import com.saasbasics.platform.modules.ai.application.dto.AiChatRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiChatResponse;
import com.saasbasics.platform.modules.ai.domain.ChatMessageData;
import com.saasbasics.platform.modules.ai.domain.ChatModelProvider;
import com.saasbasics.platform.modules.ai.domain.ChatProviderCommand;
import com.saasbasics.platform.modules.ai.domain.ChatProviderResult;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.AiVendorPresetRegistry;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiPromptVersionEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiProviderEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiProviderMapper;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一 AI 对话与流式网关服务（兼容前台抽屉接口，同时强制执行租户额度控制、Vault 解密与 Run 审计）。
 */
@Service
public class AiChatGatewayService implements AiGatewayFacade {

    private static final Logger log = LoggerFactory.getLogger(AiChatGatewayService.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final AiProviderMapper providerMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final List<ChatModelProvider> chatProviders;
    private final CredentialResolver credentialResolver;
    private final AiMeteringQuotaService meteringQuotaService;
    private final AiRunOrchestratorService runOrchestratorService;
    private final AiPromptService promptService;
    private final ObjectProvider<PlatformTelemetryService> telemetryServiceProvider;

    @Value("${saas.ai.api-key:}")
    private String fallbackApiKey;

    @Value("${saas.ai.base-url:https://api.deepseek.com}")
    private String fallbackBaseUrl;

    public AiChatGatewayService(
            AiProviderMapper providerMapper,
            AiModelConfigMapper modelConfigMapper,
            List<ChatModelProvider> chatProviders,
            CredentialResolver credentialResolver,
            AiMeteringQuotaService meteringQuotaService,
            AiRunOrchestratorService runOrchestratorService,
            AiPromptService promptService
    ) {
        this(providerMapper, modelConfigMapper, chatProviders, credentialResolver,
                meteringQuotaService, runOrchestratorService, promptService, null);
    }

    @Autowired
    public AiChatGatewayService(
            AiProviderMapper providerMapper,
            AiModelConfigMapper modelConfigMapper,
            List<ChatModelProvider> chatProviders,
            CredentialResolver credentialResolver,
            AiMeteringQuotaService meteringQuotaService,
            AiRunOrchestratorService runOrchestratorService,
            AiPromptService promptService,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider
    ) {
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.chatProviders = chatProviders != null ? chatProviders : List.of();
        this.credentialResolver = credentialResolver;
        this.meteringQuotaService = meteringQuotaService;
        this.runOrchestratorService = runOrchestratorService;
        this.promptService = promptService;
        this.telemetryServiceProvider = telemetryServiceProvider;
    }

    public List<Map<String, Object>> getModels() {
        try {
            List<AiModelConfigEntity> configs = modelConfigMapper.selectList(
                    new LambdaQueryWrapper<AiModelConfigEntity>()
                            .eq(AiModelConfigEntity::getStatus, "ENABLED")
                            .orderByAsc(AiModelConfigEntity::getSortNo)
                            .orderByAsc(AiModelConfigEntity::getId)
            );

            if (!configs.isEmpty()) {
                Map<Long, AiProviderEntity> providerMap = providerMapper.selectList(new LambdaQueryWrapper<>())
                        .stream().collect(Collectors.toMap(AiProviderEntity::getId, p -> p));

                List<Map<String, Object>> result = new ArrayList<>();
                for (AiModelConfigEntity cfg : configs) {
                    AiProviderEntity prov = providerMap.get(cfg.getProviderId());
                    String vendorName = prov != null ? prov.getProviderName() : "AI Provider";
                    boolean hasKey = prov != null && ((prov.getApiKey() != null && !prov.getApiKey().isBlank())
                            || !AiVendorPresetRegistry.requiresApiKey(prov.getProviderType()));

                    Map<String, Object> item = new HashMap<>();
                    item.put("id", cfg.getModelId());
                    item.put("name", cfg.getModelName());
                    item.put("vendor", vendorName);
                    item.put("providerType", prov != null ? prov.getProviderType() : "OPENAI_COMPATIBLE");
                    item.put("contextWindow", cfg.getContextWindow() != null ? cfg.getContextWindow() : "64k");
                    item.put("status", "CONFIGURED");
                    item.put("isDefault", cfg.getIsDefault() != null && cfg.getIsDefault() == 1);
                    item.put("providerId", cfg.getProviderId());
                    item.put("hasApiKey", hasKey);
                    result.add(item);
                }
                return result;
            }
        } catch (Exception e) {
            throw new BizException("AI_CATALOG_UNAVAILABLE", "模型目录读取失败");
        }
        return List.of();
    }

    @Override
    public ChatCompletionOutput complete(ChatCompletionCommand command) {
        AiChatRequest req = new AiChatRequest();
        req.setModel(command.modelId() != null ? command.modelId() : "deepseek-chat");
        req.setPromptCode(command.promptCode());
        req.setTemperature(command.temperature());
        req.setMessages(command.messages().stream()
                .map(m -> new AiChatRequest.ChatMessage(m.role(), m.content()))
                .toList());
        AiChatResponse resp = chat(req);
        return new ChatCompletionOutput(
                resp.getId(),
                resp.getModel(),
                resp.getReply(),
                resp.getPromptTokens() != null ? resp.getPromptTokens() : 0,
                resp.getCompletionTokens() != null ? resp.getCompletionTokens() : 0,
                resp.getCostMillis() != null ? resp.getCostMillis() : 0L
        );
    }

    public AiChatResponse chat(AiChatRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        String username = principal != null && principal.username() != null ? principal.username() : "system";

        meteringQuotaService.checkQuota(tenantId);

        long start = System.currentTimeMillis();
        String modelId = request.getModel() != null ? request.getModel() : "deepseek-chat";
        ResolvedEndpoint endpoint = resolveEndpoint(modelId);
        List<ChatMessageData> chatMessages = buildMessages(request);
        String lastPrompt = extractLastUserMessage(request);

        if (isEndpointUsable(endpoint)) {
            try {
                ChatModelProvider adapter = selectProvider(endpoint.providerType);
                ChatProviderResult remoteResp = adapter.chat(new ChatProviderCommand(
                        endpoint.providerType,
                        endpoint.providerCode,
                        endpoint.url,
                        endpoint.apiKey,
                        endpoint.targetModelId,
                        chatMessages,
                        request.getTemperature(),
                        endpoint.maxTokens,
                        30
                ));
                String runNo = runOrchestratorService.recordCompletedChatRun(
                        tenantId, userId, username, endpoint.providerCode, modelId, request.getPromptCode(),
                        lastPrompt, remoteResp.content(),
                        remoteResp.usage().promptTokens(), remoteResp.usage().completionTokens(),
                        remoteResp.latencyMs(), null
                );
                emitGenAiTelemetry(
                        endpoint.providerCode, modelId, tenantId,
                        remoteResp.usage().promptTokens(), remoteResp.usage().completionTokens(),
                        remoteResp.latencyMs(), null
                );
                return new AiChatResponse(
                        runNo,
                        modelId,
                        remoteResp.content(),
                        remoteResp.usage().promptTokens(),
                        remoteResp.usage().completionTokens(),
                        remoteResp.latencyMs()
                );
            } catch (Exception e) {
                throw new BizException("AI_PROVIDER_UNAVAILABLE", "模型服务调用失败: " + e.getMessage());
            }
        }
        throw new BizException("AI_PROVIDER_NOT_CONFIGURED", "未配置此模型的可用服务凭据");
    }

    public record FallbackCompletionResult(
            ChatCompletionOutput output,
            String usedModelId,
            String providerCode,
            boolean fallbackUsed,
            int attempts
    ) {}

    /**
     * Executes chat completion across an ordered chain of candidate model IDs, automatically falling back on failure.
     */
    public FallbackCompletionResult completeWithFallback(
            List<String> modelChain,
            String promptCode,
            List<ChatMessage> messages,
            Double temperature
    ) {
        List<String> candidates = (modelChain == null ? List.<String>of() : modelChain).stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            candidates = List.of("deepseek-chat");
        }
        Exception lastError = null;
        for (int i = 0; i < candidates.size(); i++) {
            String candidateModel = candidates.get(i);
            try {
                ResolvedEndpoint ep = resolveEndpoint(candidateModel);
                ChatCompletionOutput out = complete(new ChatCompletionCommand(candidateModel, promptCode, messages, temperature));
                String provCode = ep != null ? ep.providerCode : "unknown";
                return new FallbackCompletionResult(out, candidateModel, provCode, i > 0, i + 1);
            } catch (Exception ex) {
                lastError = ex;
                log.warn("[AI Gateway Fallback] Model [{}] attempt #{} failed: {}", candidateModel, i + 1, ex.getMessage());
            }
        }
        throw new BizException("AI_FALLBACK_EXHAUSTED",
                "全部候选模型均调用失败 (" + String.join(" -> ", candidates) + "): "
                        + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    public SseEmitter chatStream(AiChatRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        String username = principal != null && principal.username() != null ? principal.username() : "system";

        meteringQuotaService.checkQuota(tenantId);

        SseEmitter emitter = new SseEmitter(180_000L);
        String modelId = request.getModel() != null ? request.getModel() : "deepseek-chat";
        ResolvedEndpoint endpoint = resolveEndpoint(modelId);
        List<ChatMessageData> chatMessages = buildMessages(request);
        String lastPrompt = extractLastUserMessage(request);

        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                if (isEndpointUsable(endpoint)) {
                    ChatModelProvider adapter = selectProvider(endpoint.providerType);
                    StringBuilder fullBuffer = new StringBuilder();
                    AtomicInteger promptTokens = new AtomicInteger(Math.max(8, lastPrompt.length() / 2));
                    AtomicInteger compTokens = new AtomicInteger(0);
                    final boolean[] failed = {false};

                    adapter.streamChat(
                            new ChatProviderCommand(
                                    endpoint.providerType,
                                    endpoint.providerCode,
                                    endpoint.url,
                                    endpoint.apiKey,
                                    endpoint.targetModelId,
                                    chatMessages,
                                    request.getTemperature(),
                                    endpoint.maxTokens,
                                    60
                            ),
                            delta -> {
                                try {
                                    if (delta.finalUsage() != null) {
                                        if (delta.finalUsage().promptTokens() > 0) promptTokens.set(delta.finalUsage().promptTokens());
                                        if (delta.finalUsage().completionTokens() > 0) compTokens.set(delta.finalUsage().completionTokens());
                                    }
                                    if (delta.finished()) {
                                        emitter.send(SseEmitter.event().data(Map.of("done", true, "finishReason", "stop")));
                                    } else if (delta.contentDelta() != null && !delta.contentDelta().isEmpty()) {
                                        fullBuffer.append(delta.contentDelta());
                                        emitter.send(SseEmitter.event().data(Map.of(
                                                "model", modelId,
                                                "content", delta.contentDelta(),
                                                "done", false
                                        )));
                                    }
                                } catch (Exception sendEx) {
                                    failed[0] = true;
                                }
                            },
                            () -> {
                                if (!failed[0]) {
                                    int cTok = compTokens.get() > 0 ? compTokens.get() : Math.max(8, fullBuffer.length() / 2);
                                    long streamLatencyMs = System.currentTimeMillis() - start;
                                    runOrchestratorService.recordCompletedChatRun(
                                            tenantId, userId, username, endpoint.providerCode, modelId,
                                            request.getPromptCode(), lastPrompt, fullBuffer.toString(),
                                            promptTokens.get(), cTok, streamLatencyMs, null
                                    );
                                    emitGenAiTelemetry(
                                            endpoint.providerCode, modelId, tenantId,
                                            promptTokens.get(), cTok, streamLatencyMs, null
                                    );
                                    emitter.complete();
                                }
                            },
                            errCat -> emitter.completeWithError(new BizException("AI_PROVIDER_UNAVAILABLE", "模型服务中断"))
                    );
                    return;
                }

                emitter.completeWithError(new BizException("AI_PROVIDER_NOT_CONFIGURED", "未配置此模型的可用服务凭据"));
            } catch (Exception ex) {
                log.error("[AI Streaming Error]: {}", ex.getMessage());
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                }
            }
        });

        return emitter;
    }

    private void emitGenAiTelemetry(
            String providerCode,
            String modelId,
            Long tenantId,
            int inputTokens,
            int outputTokens,
            long durationMs,
            Throwable error
    ) {
        PlatformTelemetryService telemetry = telemetryServiceProvider != null
                ? telemetryServiceProvider.getIfAvailable() : null;
        if (telemetry != null) {
            telemetry.recordGenAiSpan(providerCode, modelId, tenantId, inputTokens, outputTokens, durationMs, error);
        }
    }

    private List<ChatMessageData> buildMessages(AiChatRequest request) {
        List<ChatMessageData> list = new ArrayList<>();
        String promptCode = (request.getPromptCode() != null && !request.getPromptCode().isBlank())
                ? request.getPromptCode() : "tianshu-assistant";
        AiPromptVersionEntity ver = promptService.resolveActiveVersion(promptCode);
        if (ver != null && ver.getSystemPrompt() != null && !ver.getSystemPrompt().isBlank()) {
            list.add(new ChatMessageData("system", ver.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (AiChatRequest.ChatMessage m : request.getMessages()) {
                list.add(new ChatMessageData(m.getRole(), m.getContent()));
            }
        }
        return list;
    }

    private ChatModelProvider selectProvider(String providerType) {
        return chatProviders.stream()
                .filter(p -> p.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到适配模型协议提供者: " + providerType));
    }

    private boolean isEndpointUsable(ResolvedEndpoint endpoint) {
        if (endpoint == null) {
            return false;
        }
        if (endpoint.apiKey != null && !endpoint.apiKey.isBlank()) {
            return true;
        }
        return !AiVendorPresetRegistry.requiresApiKey(endpoint.providerType);
    }

    private ResolvedEndpoint resolveEndpoint(String modelId) {
        try {
            List<AiModelConfigEntity> configs = modelConfigMapper.selectList(
                    new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getModelId, modelId)
            );
            if (!configs.isEmpty()) {
                AiModelConfigEntity modelCfg = configs.get(0);
                AiProviderEntity prov = providerMapper.selectById(modelCfg.getProviderId());
                if (prov != null) {
                    String base = prov.getBaseUrl() != null ? prov.getBaseUrl().trim() : "";
                    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    String rawKey = credentialResolver.resolvePlainSecret(prov.getTenantId(), prov.getId(), prov.getApiKey());
                    if ((rawKey == null || rawKey.isBlank()) && fallbackApiKey != null && !fallbackApiKey.isBlank()
                            && AiVendorPresetRegistry.requiresApiKey(prov.getProviderType())) {
                        rawKey = credentialResolver.resolvePlainSecret(1L, null, fallbackApiKey);
                    }
                    String resolvedType = resolveEffectiveProviderType(prov.getProviderType(), base);
                    return new ResolvedEndpoint(base, rawKey, modelCfg.getModelId(),
                            prov.getProviderCode(), resolvedType, modelCfg.getMaxTokens());
                }
            }
        } catch (Exception e) {
            log.warn("[AI Gateway]: Error resolving endpoint for {}: {}", modelId, e.getMessage());
        }

        if (fallbackApiKey != null && !fallbackApiKey.isBlank()) {
            String base = fallbackBaseUrl != null ? fallbackBaseUrl.trim() : "https://api.deepseek.com";
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return new ResolvedEndpoint(base, credentialResolver.resolvePlainSecret(1L, null, fallbackApiKey),
                    modelId, "fallback", "OPENAI_COMPATIBLE", 4096);
        }
        return null;
    }

    private String resolveEffectiveProviderType(String configuredType, String baseUrl) {
        if ("OLLAMA".equalsIgnoreCase(configuredType) && baseUrl != null && !baseUrl.endsWith("/v1")) {
            return "OLLAMA_NATIVE";
        }
        return configuredType != null ? configuredType : "OPENAI_COMPATIBLE";
    }

    private record ResolvedEndpoint(
            String url,
            String apiKey,
            String targetModelId,
            String providerCode,
            String providerType,
            Integer maxTokens
    ) {}

    private String extractLastUserMessage(AiChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "你好，请介绍一下 Antigravity SaaS 平台。";
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            AiChatRequest.ChatMessage msg = request.getMessages().get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent();
            }
        }
        return request.getMessages().get(request.getMessages().size() - 1).getContent();
    }

}
