package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.application.dto.AiCredentialRefDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiModelConfigDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiModelConfigSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiTestConnectionRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiTestConnectionResult;
import com.saasbasics.platform.modules.ai.application.dto.VaultRotateResultDTO;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.AiVendorPresetRegistry;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.AiVendorPresetRegistry.VendorPreset;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.AnthropicChatProvider;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.AzureOpenAiChatProvider;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.GeminiChatProvider;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.OllamaNativeChatProvider;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.OpenAiCompatibleChatProvider;
import com.saasbasics.platform.modules.ai.infrastructure.adapter.ProviderHttpSupport;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiCredentialRefEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiMcpServerEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiProviderEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiCredentialRefMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiMcpServerMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiProviderMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelManageService {

    private static final Logger log = LoggerFactory.getLogger(AiModelManageService.class);

    private final AiProviderMapper providerMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiCredentialRefMapper credentialRefMapper;
    private final AiMcpServerMapper mcpServerMapper;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiModelManageService(
            AiProviderMapper providerMapper,
            AiModelConfigMapper modelConfigMapper,
            AiCredentialRefMapper credentialRefMapper,
            AiMcpServerMapper mcpServerMapper,
            CredentialResolver credentialResolver,
            ObjectMapper objectMapper
    ) {
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.credentialRefMapper = credentialRefMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public List<AiProviderDTO> listProviders() {
        List<AiProviderEntity> entities = providerMapper.selectList(
                new LambdaQueryWrapper<AiProviderEntity>()
                        .orderByAsc(AiProviderEntity::getSortNo)
                        .orderByAsc(AiProviderEntity::getId)
        );

        Map<Long, Long> modelCounts = modelConfigMapper.selectList(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.groupingBy(AiModelConfigEntity::getProviderId, Collectors.counting()));

        Map<Long, AiCredentialRefEntity> credMap = credentialRefMapper.selectList(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.toMap(AiCredentialRefEntity::getProviderId, c -> c, (a, b) -> a));

        return entities.stream().map(e -> {
            AiProviderDTO dto = new AiProviderDTO();
            BeanUtils.copyProperties(e, dto);
            String storedKey = e.getApiKey();
            boolean hasKey = storedKey != null && !storedKey.isBlank();
            dto.setHasApiKey(hasKey);
            dto.setApiKeyMasked(credentialResolver.maskSecret(storedKey));
            dto.setEncryptedAtRest(hasKey && credentialResolver.isSealed(storedKey));

            AiCredentialRefEntity ref = credMap.get(e.getId());
            dto.setKeyVersion(ref != null ? ref.getKeyVersion() : (credentialResolver.isSealed(storedKey) ? credentialResolver.activeKeyVersion() : "plaintext"));
            dto.setCipherDigest(ref != null ? ref.getCipherDigest() : credentialResolver.fingerprint(storedKey));
            dto.setRotationCount(ref != null && ref.getRotationCount() != null ? ref.getRotationCount() : 0);
            dto.setModelCount(modelCounts.getOrDefault(e.getId(), 0L).intValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Long saveProvider(AiProviderSaveRequest req) {
        if (req.getProviderName() == null || req.getProviderName().isBlank()) {
            throw new IllegalArgumentException("服务商名称不能为空");
        }
        if (req.getBaseUrl() == null || req.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Base URL 不能为空");
        }
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            unsetDefaultProviders();
        }

        AiProviderEntity entity;
        boolean keyUpdated = false;
        if (req.getId() == null) {
            entity = new AiProviderEntity();
            BeanUtils.copyProperties(req, entity);
            entity.setTenantId(req.getTenantId() != null ? req.getTenantId() : currentTenantId());
            if (entity.getProviderType() == null || entity.getProviderType().isBlank()) {
                entity.setProviderType("OPENAI_COMPATIBLE");
            }
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("ENABLED");
            }
            if (entity.getIsDefault() == null) {
                entity.setIsDefault(0);
            }
            if (entity.getSortNo() == null) {
                entity.setSortNo(50);
            }
            if (req.getApiKey() != null && !req.getApiKey().isBlank() && !req.getApiKey().contains("****")) {
                entity.setApiKey(credentialResolver.sealSecret(req.getApiKey().trim()));
                keyUpdated = true;
            }
            providerMapper.insert(entity);
        } else {
            entity = providerMapper.selectById(req.getId());
            if (entity == null) {
                throw new IllegalArgumentException("未找到待更新的服务商: " + req.getId());
            }
            entity.setProviderName(req.getProviderName());
            entity.setProviderCode(req.getProviderCode());
            entity.setProviderType(req.getProviderType() != null ? req.getProviderType() : "OPENAI_COMPATIBLE");
            entity.setBaseUrl(req.getBaseUrl().trim());

            String newKey = req.getApiKey();
            if (newKey != null && !newKey.isBlank() && !newKey.contains("****")) {
                entity.setApiKey(credentialResolver.sealSecret(newKey.trim()));
                keyUpdated = true;
            }
            if (req.getStatus() != null) entity.setStatus(req.getStatus());
            if (req.getIsDefault() != null) entity.setIsDefault(req.getIsDefault());
            if (req.getSortNo() != null) entity.setSortNo(req.getSortNo());
            if (req.getRemark() != null) entity.setRemark(req.getRemark());

            providerMapper.updateById(entity);
        }

        upsertCredentialRef(entity, keyUpdated);
        return entity.getId();
    }

    @Transactional
    public void deleteProvider(Long id) {
        AiProviderEntity existing = providerMapper.selectById(id);
        if (existing == null) return;
        providerMapper.deleteById(id);
        modelConfigMapper.delete(
                new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getProviderId, id)
        );
        credentialRefMapper.delete(
                new LambdaQueryWrapper<AiCredentialRefEntity>().eq(AiCredentialRefEntity::getProviderId, id)
        );
    }

    public List<AiCredentialRefDTO> listCredentialRefs() {
        return credentialRefMapper.selectList(
                new LambdaQueryWrapper<AiCredentialRefEntity>().orderByAsc(AiCredentialRefEntity::getProviderId)
        ).stream().map(c -> new AiCredentialRefDTO(
                c.getId(),
                c.getTenantId(),
                c.getCredentialCode(),
                c.getProviderId(),
                c.getKeyAlias(),
                c.getMaskedHint(),
                c.getEncryptionAlgo(),
                c.getKeyVersion(),
                c.getCipherDigest(),
                c.getRotationCount(),
                c.getLastRotatedAt(),
                c.getStatus(),
                c.getRemark()
        )).toList();
    }

    @Transactional
    public VaultRotateResultDTO rotateAllCredentials() {
        List<AiProviderEntity> providers = providerMapper.selectList(new LambdaQueryWrapper<>());
        int rotatedProviders = 0;
        for (AiProviderEntity prov : providers) {
            if (prov.getApiKey() != null && !prov.getApiKey().isBlank()) {
                String rotatedCipher = credentialResolver.rotateSecret(prov.getApiKey());
                prov.setApiKey(rotatedCipher);
                providerMapper.updateById(prov);
                upsertCredentialRef(prov, true);
                rotatedProviders++;
            } else {
                upsertCredentialRef(prov, false);
            }
        }

        List<AiMcpServerEntity> mcpServers = mcpServerMapper.selectList(new LambdaQueryWrapper<>());
        int rotatedMcps = 0;
        for (AiMcpServerEntity mcp : mcpServers) {
            if (mcp.getEncryptedAuthToken() != null && !mcp.getEncryptedAuthToken().isBlank()) {
                mcp.setEncryptedAuthToken(credentialResolver.rotateSecret(mcp.getEncryptedAuthToken()));
                mcpServerMapper.updateById(mcp);
                rotatedMcps++;
            }
        }

        String ver = credentialResolver.activeKeyVersion();
        return new VaultRotateResultDTO(
                rotatedProviders,
                rotatedMcps,
                ver,
                "Vault AES-256-GCM 密钥轮换与密文升级完成（服务商 " + rotatedProviders + " 个，MCP " + rotatedMcps + " 个，激活版本 " + ver + "）"
        );
    }

    public List<AiModelConfigDTO> listModels(Long providerId, String status) {
        LambdaQueryWrapper<AiModelConfigEntity> qw = new LambdaQueryWrapper<AiModelConfigEntity>()
                .orderByAsc(AiModelConfigEntity::getSortNo)
                .orderByAsc(AiModelConfigEntity::getId);
        if (providerId != null) {
            qw.eq(AiModelConfigEntity::getProviderId, providerId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(AiModelConfigEntity::getStatus, status);
        }

        List<AiModelConfigEntity> models = modelConfigMapper.selectList(qw);
        Map<Long, AiProviderEntity> providerMap = providerMapper.selectList(new LambdaQueryWrapper<>())
                .stream().collect(Collectors.toMap(AiProviderEntity::getId, p -> p));

        return models.stream().map(m -> {
            AiModelConfigDTO dto = new AiModelConfigDTO();
            BeanUtils.copyProperties(m, dto);
            AiProviderEntity prov = providerMap.get(m.getProviderId());
            if (prov != null) {
                dto.setProviderName(prov.getProviderName());
                dto.setProviderCode(prov.getProviderCode());
                dto.setProviderType(prov.getProviderType());
                dto.setHasApiKey((prov.getApiKey() != null && !prov.getApiKey().isBlank())
                        || !AiVendorPresetRegistry.requiresApiKey(prov.getProviderType()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Long saveModel(AiModelConfigSaveRequest req) {
        if (req.getModelId() == null || req.getModelId().isBlank()) {
            throw new IllegalArgumentException("模型标识符 (model_id) 不能为空");
        }
        if (req.getModelName() == null || req.getModelName().isBlank()) {
            throw new IllegalArgumentException("模型展示名称不能为空");
        }
        if (req.getProviderId() == null) {
            throw new IllegalArgumentException("必须指定所属服务商 (providerId)");
        }
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            unsetDefaultModels();
        }

        if (req.getId() == null) {
            AiModelConfigEntity entity = new AiModelConfigEntity();
            BeanUtils.copyProperties(req, entity);
            entity.setTenantId(req.getTenantId() != null ? req.getTenantId() : currentTenantId());
            if (entity.getStatus() == null || entity.getStatus().isBlank()) entity.setStatus("ENABLED");
            if (entity.getIsDefault() == null) entity.setIsDefault(0);
            if (entity.getSortNo() == null) entity.setSortNo(50);
            modelConfigMapper.insert(entity);
            return entity.getId();
        } else {
            AiModelConfigEntity existing = modelConfigMapper.selectById(req.getId());
            if (existing == null) {
                throw new IllegalArgumentException("未找到待更新的模型: " + req.getId());
            }
            existing.setProviderId(req.getProviderId());
            existing.setModelId(req.getModelId().trim());
            existing.setModelName(req.getModelName().trim());
            if (req.getCategory() != null) existing.setCategory(req.getCategory());
            if (req.getContextWindow() != null) existing.setContextWindow(req.getContextWindow());
            if (req.getMaxTokens() != null) existing.setMaxTokens(req.getMaxTokens());
            if (req.getTemperature() != null) existing.setTemperature(req.getTemperature());
            if (req.getIsDefault() != null) existing.setIsDefault(req.getIsDefault());
            if (req.getStatus() != null) existing.setStatus(req.getStatus());
            if (req.getSortNo() != null) existing.setSortNo(req.getSortNo());

            modelConfigMapper.updateById(existing);
            return existing.getId();
        }
    }

    @Transactional
    public void deleteModel(Long id) {
        modelConfigMapper.deleteById(id);
    }

    public record DiscoveredModelDTO(
            String modelId,
            String modelName,
            String category,
            String contextWindow,
            boolean alreadyConfigured
    ) {}

    public List<VendorPreset> listVendorPresets() {
        return AiVendorPresetRegistry.all();
    }

    public List<DiscoveredModelDTO> discoverModels(Long providerId) {
        AiProviderEntity prov = providerMapper.selectById(providerId);
        if (prov == null) {
            throw new IllegalArgumentException("未找到服务商: " + providerId);
        }
        String rawKey = credentialResolver.resolvePlainSecret(prov.getTenantId(), prov.getId(), prov.getApiKey());
        String baseUrl = trimTrailingSlashes(prov.getBaseUrl());
        String pType = prov.getProviderType() != null ? prov.getProviderType().toUpperCase() : "OPENAI_COMPATIBLE";

        var existingIds = modelConfigMapper.selectList(
                        new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getProviderId, providerId))
                .stream()
                .map(AiModelConfigEntity::getModelId)
                .collect(Collectors.toSet());

        List<DiscoveredModelDTO> discovered = new java.util.ArrayList<>();
        try {
            if ("OLLAMA".equals(pType) || "OLLAMA_NATIVE".equals(pType)) {
                String ollamaBase = baseUrl.endsWith("/v1") ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
                URI uri = ProviderHttpSupport.validateEndpoint(ollamaBase + "/api/tags", null);
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode models = objectMapper.readTree(resp.body()).path("models");
                    if (models.isArray()) {
                        for (JsonNode m : models) {
                            String id = m.path("name").asText();
                            if (!id.isBlank()) {
                                discovered.add(new DiscoveredModelDTO(id, id, guessCategory(id), "32k", existingIds.contains(id)));
                            }
                        }
                    }
                }
            } else if ("GEMINI".equals(pType)) {
                String prefix = baseUrl.endsWith("/models") ? baseUrl : (baseUrl + "/models");
                URI uri = ProviderHttpSupport.validateEndpoint(prefix, null);
                HttpRequest.Builder reqB = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET();
                if (rawKey != null && !rawKey.isBlank()) reqB.header("x-goog-api-key", rawKey.trim());
                HttpResponse<String> resp = httpClient.send(reqB.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode models = objectMapper.readTree(resp.body()).path("models");
                    if (models.isArray()) {
                        for (JsonNode m : models) {
                            String rawName = m.path("name").asText("");
                            String id = rawName.startsWith("models/") ? rawName.substring(7) : rawName;
                            String display = m.path("displayName").asText(id);
                            if (!id.isBlank()) {
                                discovered.add(new DiscoveredModelDTO(id, display, guessCategory(id), "1M", existingIds.contains(id)));
                            }
                        }
                    }
                }
            } else if ("ANTHROPIC".equals(pType)) {
                String modelsUrl = baseUrl.endsWith("/v1") ? (baseUrl + "/models") : (baseUrl + "/v1/models");
                URI uri = ProviderHttpSupport.validateEndpoint(modelsUrl, null);
                HttpRequest.Builder reqB = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("anthropic-version", "2023-06-01")
                        .GET();
                if (rawKey != null && !rawKey.isBlank()) reqB.header("x-api-key", rawKey.trim());
                HttpResponse<String> resp = httpClient.send(reqB.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode data = objectMapper.readTree(resp.body()).path("data");
                    if (data.isArray()) {
                        for (JsonNode m : data) {
                            String id = m.path("id").asText();
                            String display = m.path("display_name").asText(id);
                            if (!id.isBlank()) {
                                discovered.add(new DiscoveredModelDTO(id, display, guessCategory(id), "200k", existingIds.contains(id)));
                            }
                        }
                    }
                }
            } else {
                String modelsUrl = baseUrl.endsWith("/models") ? baseUrl : (baseUrl + "/models");
                URI uri = ProviderHttpSupport.validateEndpoint(modelsUrl, null);
                HttpRequest.Builder reqB = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET();
                if (rawKey != null && !rawKey.isBlank()) reqB.header("Authorization", "Bearer " + rawKey.trim());
                HttpResponse<String> resp = httpClient.send(reqB.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode data = objectMapper.readTree(resp.body()).path("data");
                    if (data.isArray()) {
                        for (JsonNode m : data) {
                            String id = m.path("id").asText();
                            if (!id.isBlank()) {
                                discovered.add(new DiscoveredModelDTO(id, id, guessCategory(id), "128k", existingIds.contains(id)));
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[AI Discover Models] {}: {}", prov.getProviderCode(), ex.getMessage());
        }

        if (discovered.isEmpty()) {
            AiVendorPresetRegistry.all().stream()
                    .filter(p -> p.vendorCode().equalsIgnoreCase(prov.getProviderCode())
                            || p.providerType().equalsIgnoreCase(pType))
                    .findFirst()
                    .ifPresent(preset -> preset.suggestedModels().forEach(t ->
                            discovered.add(new DiscoveredModelDTO(
                                    t.modelId(), t.modelName(), t.category(), t.contextWindow(),
                                    existingIds.contains(t.modelId())))));
        }
        return discovered;
    }

    public AiTestConnectionResult testConnection(AiTestConnectionRequest req) {
        long start = System.currentTimeMillis();
        String targetBaseUrl = req.getBaseUrl();
        String targetApiKey = req.getApiKey();
        String testModel = req.getTestModel();
        String providerType = req.getProviderType();

        if (req.getProviderId() != null) {
            AiProviderEntity prov = providerMapper.selectById(req.getProviderId());
            if (prov != null) {
                if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
                    targetBaseUrl = prov.getBaseUrl();
                }
                if (providerType == null || providerType.isBlank()) {
                    providerType = prov.getProviderType();
                }
                if (targetApiKey == null || targetApiKey.isBlank() || targetApiKey.contains("****")) {
                    targetApiKey = credentialResolver.resolvePlainSecret(prov.getTenantId(), prov.getId(), prov.getApiKey());
                }
            }
        }

        if (targetBaseUrl == null || targetBaseUrl.isBlank()) {
            return new AiTestConnectionResult(false, 0, "Base URL 为空，无法发起测试", null);
        }

        String pType = providerType != null ? providerType.trim().toUpperCase() : "OPENAI_COMPATIBLE";
        String normalizedUrl = trimTrailingSlashes(targetBaseUrl);
        if (testModel == null || testModel.isBlank()) {
            testModel = switch (pType) {
                case "ANTHROPIC" -> "claude-sonnet-4-20250514";
                case "GEMINI" -> "gemini-2.5-flash";
                case "AZURE_OPENAI" -> "gpt-4o";
                case "OLLAMA", "OLLAMA_NATIVE" -> "llama3.1:8b";
                default -> "deepseek-chat";
            };
        }

        try {
            String endpoint;
            HttpRequest.Builder httpReqBuilder;
            if ("ANTHROPIC".equals(pType)) {
                endpoint = AnthropicChatProvider.buildMessagesUrl(normalizedUrl);
                URI uri = ProviderHttpSupport.validateEndpoint(endpoint, null);
                Map<String, Object> bodyMap = Map.of(
                        "model", testModel,
                        "max_tokens", 8,
                        "messages", List.of(Map.of("role", "user", "content", "hi"))
                );
                httpReqBuilder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));
                if (targetApiKey != null && !targetApiKey.isBlank()) {
                    httpReqBuilder.header("x-api-key", targetApiKey.trim());
                }
            } else if ("GEMINI".equals(pType)) {
                endpoint = GeminiChatProvider.buildGenerateUrl(normalizedUrl, testModel, false);
                URI uri = ProviderHttpSupport.validateEndpoint(endpoint, null);
                Map<String, Object> bodyMap = Map.of(
                        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "hi")))),
                        "generationConfig", Map.of("maxOutputTokens", 8)
                );
                httpReqBuilder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));
                if (targetApiKey != null && !targetApiKey.isBlank()) {
                    httpReqBuilder.header("x-goog-api-key", targetApiKey.trim());
                }
            } else if ("AZURE_OPENAI".equals(pType)) {
                endpoint = AzureOpenAiChatProvider.buildDeploymentUrl(normalizedUrl, testModel);
                URI uri = ProviderHttpSupport.validateEndpoint(endpoint, null);
                Map<String, Object> bodyMap = Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "hi")),
                        "max_tokens", 8
                );
                httpReqBuilder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));
                if (targetApiKey != null && !targetApiKey.isBlank()) {
                    httpReqBuilder.header("api-key", targetApiKey.trim());
                }
            } else if ("OLLAMA_NATIVE".equals(pType) || ("OLLAMA".equals(pType) && !normalizedUrl.endsWith("/v1"))) {
                endpoint = OllamaNativeChatProvider.buildChatUrl(normalizedUrl);
                URI uri = ProviderHttpSupport.validateEndpoint(endpoint, null);
                Map<String, Object> bodyMap = Map.of(
                        "model", testModel,
                        "stream", false,
                        "messages", List.of(Map.of("role", "user", "content", "hi"))
                );
                httpReqBuilder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));
            } else {
                endpoint = OpenAiCompatibleChatProvider.buildCompletionsUrl(normalizedUrl);
                URI uri = ProviderHttpSupport.validateEndpoint(endpoint, null);
                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("model", testModel);
                bodyMap.put("messages", List.of(Map.of("role", "user", "content", "hi")));
                bodyMap.put("max_tokens", 8);
                bodyMap.put("stream", false);
                httpReqBuilder = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyMap)));
                if (targetApiKey != null && !targetApiKey.isBlank()) {
                    httpReqBuilder.header("Authorization", "Bearer " + targetApiKey.trim());
                }
            }

            HttpResponse<String> response = httpClient.send(httpReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                String replySample = extractProbeSnippet(responseBody, pType);
                return new AiTestConnectionResult(true, latency,
                        String.format("握手成功 [%s] (HTTP 200)，时延: %d ms", pType, latency), replySample);
            } else if (statusCode == 401 || statusCode == 403) {
                return new AiTestConnectionResult(false, latency, "认证失败 (HTTP " + statusCode + ")：API Key 无效或权限不足", responseBody);
            } else if (statusCode == 404) {
                return new AiTestConnectionResult(false, latency, String.format("端点未找到 (HTTP 404)：请检查 Base URL [%s]", endpoint), responseBody);
            } else if (statusCode == 429) {
                return new AiTestConnectionResult(false, latency, "调用超限或配额不足 (HTTP 429)", responseBody);
            } else {
                return new AiTestConnectionResult(false, latency, String.format("服务端返回状态码: HTTP %d", statusCode), responseBody);
            }
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.warn("[AI Connection Test Failed]: {}", ex.getMessage());
            return new AiTestConnectionResult(false, latency, "连接失败: " + ex.getMessage(), null);
        }
    }

    private String extractProbeSnippet(String responseBody, String pType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = switch (pType) {
                case "ANTHROPIC" -> root.at("/content/0/text");
                case "GEMINI" -> root.at("/candidates/0/content/parts/0/text");
                case "OLLAMA_NATIVE", "OLLAMA" -> {
                    JsonNode n = root.at("/message/content");
                    yield n.isMissingNode() ? root.at("/choices/0/message/content") : n;
                }
                default -> root.at("/choices/0/message/content");
            };
            if (!contentNode.isMissingNode() && !contentNode.asText().isBlank()) {
                return contentNode.asText().trim();
            }
        } catch (Exception ignored) {
        }
        return "握手成功";
    }

    private static String trimTrailingSlashes(String url) {
        String clean = url == null ? "" : url.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String guessCategory(String modelId) {
        String low = modelId.toLowerCase();
        if (low.contains("reason") || low.contains("-r1") || low.startsWith("o1") || low.startsWith("o3")
                || low.contains("thinking") || low.contains("pro")) {
            return "REASONING";
        }
        if (low.contains("code")) {
            return "CODER";
        }
        return "CHAT";
    }

    private void upsertCredentialRef(AiProviderEntity provider, boolean keyRotatedOrUpdated) {
        String code = "cred_" + provider.getProviderCode() + "_" + provider.getId();
        AiCredentialRefEntity existing = credentialRefMapper.selectOne(
                new LambdaQueryWrapper<AiCredentialRefEntity>()
                        .eq(AiCredentialRefEntity::getProviderId, provider.getId())
                        .last("LIMIT 1")
        );
        String storedKey = provider.getApiKey();
        boolean hasKey = storedKey != null && !storedKey.isBlank();
        boolean sealed = hasKey && credentialResolver.isSealed(storedKey);

        if (existing == null) {
            existing = new AiCredentialRefEntity();
            existing.setTenantId(provider.getTenantId() != null ? provider.getTenantId() : currentTenantId());
            existing.setCredentialCode(code);
            existing.setProviderId(provider.getId());
            existing.setRotationCount(keyRotatedOrUpdated ? 1 : 0);
        } else if (keyRotatedOrUpdated) {
            existing.setRotationCount((existing.getRotationCount() != null ? existing.getRotationCount() : 0) + 1);
        }

        existing.setKeyAlias(provider.getProviderName() + " API Key");
        existing.setMaskedHint(credentialResolver.maskSecret(storedKey));
        existing.setEncryptionAlgo("AES-256-GCM");
        existing.setKeyVersion(sealed ? credentialResolver.activeKeyVersion() : (hasKey ? "plaintext" : credentialResolver.activeKeyVersion()));
        existing.setCipherDigest(credentialResolver.fingerprint(storedKey));
        existing.setStatus(!hasKey || sealed ? "ACTIVE" : "LEGACY_PLAINTEXT");
        if (keyRotatedOrUpdated) {
            existing.setLastRotatedAt(LocalDateTime.now());
        }
        existing.setRemark("Vault 自动托管凭据引用");

        if (existing.getId() == null) {
            credentialRefMapper.insert(existing);
        } else {
            credentialRefMapper.updateById(existing);
        }
    }

    private void unsetDefaultProviders() {
        List<AiProviderEntity> list = providerMapper.selectList(
                new LambdaQueryWrapper<AiProviderEntity>().eq(AiProviderEntity::getIsDefault, 1)
        );
        for (AiProviderEntity p : list) {
            p.setIsDefault(0);
            providerMapper.updateById(p);
        }
    }

    private void unsetDefaultModels() {
        List<AiModelConfigEntity> list = modelConfigMapper.selectList(
                new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getIsDefault, 1)
        );
        for (AiModelConfigEntity m : list) {
            m.setIsDefault(0);
            modelConfigMapper.updateById(m);
        }
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
