package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.trace.TraceContext;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.modules.ai.application.dto.AiRunCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiRunDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiRunStepDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiToolCallDTO;
import com.saasbasics.platform.modules.ai.domain.ChatMessageData;
import com.saasbasics.platform.modules.ai.domain.ChatModelProvider;
import com.saasbasics.platform.modules.ai.domain.ChatProviderCommand;
import com.saasbasics.platform.modules.ai.domain.ChatProviderResult;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.modules.ai.domain.RunState;
import com.saasbasics.platform.modules.ai.domain.ToolCallApprovalStatus;
import com.saasbasics.platform.modules.ai.domain.ToolCallExecutionStatus;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiModelConfigEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiPromptVersionEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiProviderEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRunStepEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolCallEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiProviderMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunStepMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiToolCallMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI Run 编排引擎与状态机服务 (CREATED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> SUCCEEDED / REJECTED)。
 * <p>
 * 强制安全契约：
 * <ul>
 *   <li>Run 始终从 AuthContext 固化 tenantId/userId/权限上下文，绝不信任请求传参</li>
 *   <li>工具调用复用业务权限码，无“AI 超级权限”</li>
 *   <li>HIGH 风险写入工具进入 PENDING_APPROVAL 挂起待人审，审批通过执行前重新校验权限与幂等键</li>
 * </ul>
 */
@Service
public class AiRunOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AiRunOrchestratorService.class);

    private final AiRunMapper runMapper;
    private final AiRunStepMapper stepMapper;
    private final AiToolCallMapper toolCallMapper;
    private final AiProviderMapper providerMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final AiMeteringQuotaService meteringQuotaService;
    private final AiPromptService promptService;
    private final AiMcpGovernanceService mcpGovernanceService;
    private final List<ChatModelProvider> chatProviders;
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    public AiRunOrchestratorService(
            AiRunMapper runMapper,
            AiRunStepMapper stepMapper,
            AiToolCallMapper toolCallMapper,
            AiProviderMapper providerMapper,
            AiModelConfigMapper modelConfigMapper,
            AiMeteringQuotaService meteringQuotaService,
            AiPromptService promptService,
            AiMcpGovernanceService mcpGovernanceService,
            List<ChatModelProvider> chatProviders,
            CredentialResolver credentialResolver,
            ObjectMapper objectMapper
    ) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.toolCallMapper = toolCallMapper;
        this.providerMapper = providerMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.meteringQuotaService = meteringQuotaService;
        this.promptService = promptService;
        this.mcpGovernanceService = mcpGovernanceService;
        this.chatProviders = chatProviders != null ? chatProviders : List.of();
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
    }

    public List<AiRunDTO> listRuns() {
        List<AiRunEntity> runs = runMapper.selectList(
                new LambdaQueryWrapper<AiRunEntity>()
                        .orderByDesc(AiRunEntity::getId)
                        .last("LIMIT 30")
        );
        return runs.stream().map(r -> toRunDto(r, false)).toList();
    }

    public AiRunDTO getRunDetail(Long runId) {
        AiRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException("AI_RUN_NOT_FOUND", "未找到 AI Run 记录: " + runId);
        }
        return toRunDto(run, true);
    }

    public List<AiToolCallDTO> listPendingApprovals() {
        return toolCallMapper.selectList(
                new LambdaQueryWrapper<AiToolCallEntity>()
                        .eq(AiToolCallEntity::getApprovalStatus, ToolCallApprovalStatus.PENDING_APPROVAL.name())
                        .orderByDesc(AiToolCallEntity::getId)
        ).stream().map(this::toToolCallDto).toList();
    }

    @Transactional
    public AiRunDTO executeRun(AiRunCreateRequest req) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        String username = principal != null && principal.username() != null ? principal.username() : "system";

        // 1. Check tenant token budget BEFORE running
        meteringQuotaService.checkQuota(tenantId);

        String modelId = (req.modelId() != null && !req.modelId().isBlank()) ? req.modelId().trim() : "deepseek-chat";
        String inputPrompt = (req.inputPrompt() != null && !req.inputPrompt().isBlank())
                ? req.inputPrompt().trim() : "请执行当前平台诊断任务。";

        AiPromptVersionEntity promptVersion = promptService.resolveActiveVersion(req.promptCode());

        // 2. Create Run in CREATED state
        AiRunEntity run = new AiRunEntity();
        run.setTenantId(tenantId);
        run.setRunNo("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        run.setUserId(userId);
        run.setUsername(username);
        run.setModelId(modelId);
        run.setProviderCode("gateway");
        run.setPromptCode(req.promptCode());
        run.setPromptVersion(promptVersion != null ? promptVersion.getVersionNo() : null);
        run.setStatus(RunState.CREATED.name());
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        run.setEstimatedCost(BigDecimal.ZERO);
        run.setLatencyMs(0L);
        run.setTraceId(TraceContext.getTraceId());
        run.setInputSummary(truncate(inputPrompt, 800));
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        // Transition CREATED -> RUNNING
        transitionRunState(run, RunState.RUNNING);
        runMapper.updateById(run);

        long startMs = System.currentTimeMillis();
        int stepNo = 1;

        // 3. Optional Tool / MCP Execution or Approval Gate
        if (req.toolCode() != null && !req.toolCode().isBlank()) {
            AiToolEntity tool = mcpGovernanceService.findToolByCode(req.toolCode().trim());
            if (tool == null) {
                transitionRunState(run, RunState.FAILED);
                run.setErrorType("TOOL_NOT_FOUND");
                run.setErrorMessage("未注册的工具: " + req.toolCode());
                run.setFinishedAt(LocalDateTime.now());
                run.setLatencyMs(System.currentTimeMillis() - startMs);
                runMapper.updateById(run);
                return toRunDto(run, true);
            }

            boolean permissionGranted = hasPermission(principal, tool.getPermissionCode());
            AiToolCallEntity call = new AiToolCallEntity();
            call.setTenantId(tenantId);
            call.setRunId(run.getId());
            call.setCallNo("tc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            call.setToolCode(tool.getToolCode());
            call.setAdapterType(tool.getAdapterType());
            call.setRiskLevel(tool.getRiskLevel());
            call.setPermissionCode(tool.getPermissionCode());
            call.setIdempotencyKey("idem_" + run.getRunNo() + "_" + tool.getToolCode());
            call.setArgumentsSummary(toJsonSafe(req.toolArguments()));
            call.setPermissionGranted(permissionGranted ? 1 : 0);

            if (!permissionGranted) {
                call.setApprovalStatus(ToolCallApprovalStatus.REJECTED.name());
                call.setExecutionStatus(ToolCallExecutionStatus.SKIPPED.name());
                call.setRejectReason("权限校验拒绝：当前主体缺少权限码 [" + tool.getPermissionCode() + "]");
                toolCallMapper.insert(call);

                insertStep(run, stepNo++, "TOOL_DISPATCH", null, tool.getToolCode(), "FAILED", 0, 0, 0L,
                        "PERMISSION_DENIED", Map.of("permissionCode", tool.getPermissionCode()));

                transitionRunState(run, RunState.FAILED);
                run.setErrorType("PERMISSION_DENIED");
                run.setErrorMessage(call.getRejectReason());
                run.setFinishedAt(LocalDateTime.now());
                run.setLatencyMs(System.currentTimeMillis() - startMs);
                runMapper.updateById(run);
                return toRunDto(run, true);
            }

            if (ToolRiskLevel.HIGH.name().equalsIgnoreCase(tool.getRiskLevel())) {
                call.setApprovalStatus(ToolCallApprovalStatus.PENDING_APPROVAL.name());
                call.setExecutionStatus(ToolCallExecutionStatus.PENDING.name());
                toolCallMapper.insert(call);

                insertStep(run, stepNo, "APPROVAL_GATE", null, tool.getToolCode(), "WAITING_APPROVAL", 0, 0, 0L,
                        null, Map.of("callNo", call.getCallNo(), "riskLevel", "HIGH"));

                transitionRunState(run, RunState.WAITING_APPROVAL);
                run.setOutputSummary("检测到高危写操作工具 [" + tool.getToolCode() + "]，Run 已挂起等待管理员人工审批（ToolCall: " + call.getCallNo() + "）。");
                run.setLatencyMs(System.currentTimeMillis() - startMs);
                runMapper.updateById(run);
                return toRunDto(run, true);
            }

            // Auto-approved READ_ONLY / LOW risk tool execution
            call.setApprovalStatus(ToolCallApprovalStatus.AUTO_APPROVED.name());
            call.setExecutionStatus(ToolCallExecutionStatus.RUNNING.name());
            toolCallMapper.insert(call);

            long toolStart = System.currentTimeMillis();
            AiToolExecutionResult toolResult = mcpGovernanceService.executeAuthorizedTool(tool, tenantId, userId, req.toolArguments());
            long toolLatency = System.currentTimeMillis() - toolStart;
            call.setExecutionStatus(toolResult.success() ? ToolCallExecutionStatus.SUCCEEDED.name() : ToolCallExecutionStatus.FAILED.name());
            call.setResultSummary(truncate(toolResult.payloadJson(), 2000));
            call.setLatencyMs(toolLatency);
            toolCallMapper.updateById(call);

            insertStep(run, stepNo++, "MCP".equalsIgnoreCase(tool.getAdapterType()) ? "MCP_CALL" : "TOOL_DISPATCH",
                    null, tool.getToolCode(), call.getExecutionStatus(), 0, 0, toolLatency,
                    toolResult.success() ? null : "TOOL_EXECUTION_ERROR",
                    Map.of("summary", toolResult.summary(), "callNo", call.getCallNo()));

            if (!toolResult.success()) {
                transitionRunState(run, RunState.FAILED);
                run.setErrorType("TOOL_EXECUTION_ERROR");
                run.setErrorMessage(toolResult.summary());
                run.setFinishedAt(LocalDateTime.now());
                run.setLatencyMs(System.currentTimeMillis() - startMs);
                runMapper.updateById(run);
                return toRunDto(run, true);
            }

            // Synthesize final run output combining tool evidence & model context
            int estPrompt = Math.max(12, inputPrompt.length() / 2);
            int estComp = Math.max(24, toolResult.summary().length() / 2);
            finishRunSucceeded(run, stepNo, "tool-assisted", modelId,
                    "工具 [" + tool.getToolCode() + "] 执行完成：" + toolResult.summary() + "\n明细载荷：" + toolResult.payloadJson(),
                    estPrompt, estComp, startMs);
            return toRunDto(run, true);
        }

        // 4. Direct Model Invocation Step
        executeModelStepAndFinish(run, stepNo, modelId, promptVersion, inputPrompt, startMs);
        return toRunDto(run, true);
    }

    @Transactional
    public AiRunDTO approveToolCall(Long callId, String reason) {
        AiToolCallEntity call = toolCallMapper.selectById(callId);
        if (call == null) {
            throw new BizException("AI_TOOL_CALL_NOT_FOUND", "未找到待审批的工具调用: " + callId);
        }
        AiRunEntity run = runMapper.selectById(call.getRunId());
        if (run == null) {
            throw new BizException("AI_RUN_NOT_FOUND", "关联 Run 不存在");
        }

        AuthPrincipal principal = AuthContext.get();
        Long currentTenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        if (!Objects.equals(run.getTenantId(), currentTenantId)) {
            throw new BizException("AUTH_FORBIDDEN", "禁止跨租户审批 AI 工具调用");
        }

        // Idempotency & Replay Protection
        if (ToolCallExecutionStatus.SUCCEEDED.name().equalsIgnoreCase(call.getExecutionStatus())) {
            throw new BizException("AI_TOOL_CALL_ALREADY_EXECUTED", "该高危工具调用已执行完成，幂等键校验拒绝重复执行");
        }
        if (!ToolCallApprovalStatus.PENDING_APPROVAL.name().equalsIgnoreCase(call.getApprovalStatus())) {
            throw new BizException("AI_TOOL_CALL_APPROVAL_INVALID", "当前工具调用状态不可审批: " + call.getApprovalStatus());
        }

        // Re-verify business permission on execution
        if (!hasPermission(principal, call.getPermissionCode())) {
            throw new BizException("AUTH_FORBIDDEN", "审批执行权限复核失败，缺少权限码: " + call.getPermissionCode());
        }

        Long approverId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        call.setApprovalStatus(ToolCallApprovalStatus.APPROVED.name());
        call.setApprovedBy(approverId);
        call.setApprovedAt(LocalDateTime.now());
        call.setRejectReason(reason);
        call.setExecutionStatus(ToolCallExecutionStatus.RUNNING.name());
        toolCallMapper.updateById(call);

        // Transition Run: WAITING_APPROVAL -> RUNNING
        transitionRunState(run, RunState.RUNNING);
        runMapper.updateById(run);

        AiToolEntity tool = mcpGovernanceService.findToolByCode(call.getToolCode());
        Map<String, Object> args = parseJsonSafe(call.getArgumentsSummary());
        long toolStart = System.currentTimeMillis();
        AiToolExecutionResult result = tool != null
                ? mcpGovernanceService.executeAuthorizedTool(tool, run.getTenantId(), approverId, args)
                : AiToolExecutionResult.error("工具注册记录已被移除");
        long toolLatency = System.currentTimeMillis() - toolStart;

        call.setExecutionStatus(result.success() ? ToolCallExecutionStatus.SUCCEEDED.name() : ToolCallExecutionStatus.FAILED.name());
        call.setResultSummary(truncate(result.payloadJson(), 2000));
        call.setLatencyMs(toolLatency);
        toolCallMapper.updateById(call);

        int nextStep = stepMapper.selectList(new LambdaQueryWrapper<AiRunStepEntity>().eq(AiRunStepEntity::getRunId, run.getId())).size() + 1;
        insertStep(run, nextStep, "TOOL_DISPATCH", run.getModelId(), call.getToolCode(),
                call.getExecutionStatus(), 0, 0, toolLatency,
                result.success() ? null : "APPROVED_TOOL_FAILED",
                Map.of("approvedBy", approverId, "reason", reason != null ? reason : "人工审批放行", "summary", result.summary()));

        if (result.success()) {
            finishRunSucceeded(run, nextStep + 1, "human-approved", run.getModelId(),
                    "人工审批已放行并完成高危操作 [" + call.getToolCode() + "]：" + result.summary(),
                    20, 40, System.currentTimeMillis() - toolLatency);
        } else {
            transitionRunState(run, RunState.FAILED);
            run.setErrorType("APPROVED_TOOL_FAILED");
            run.setErrorMessage(result.summary());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
        }
        return toRunDto(run, true);
    }

    @Transactional
    public AiRunDTO rejectToolCall(Long callId, String reason) {
        AiToolCallEntity call = toolCallMapper.selectById(callId);
        if (call == null) {
            throw new BizException("AI_TOOL_CALL_NOT_FOUND", "未找到待审批的工具调用: " + callId);
        }
        AiRunEntity run = runMapper.selectById(call.getRunId());
        if (run == null) {
            throw new BizException("AI_RUN_NOT_FOUND", "关联 Run 不存在");
        }
        if (!ToolCallApprovalStatus.PENDING_APPROVAL.name().equalsIgnoreCase(call.getApprovalStatus())) {
            throw new BizException("AI_TOOL_CALL_APPROVAL_INVALID", "当前工具调用状态不可驳回: " + call.getApprovalStatus());
        }

        AuthPrincipal principal = AuthContext.get();
        Long approverId = principal != null && principal.userId() != null ? principal.userId() : 1L;
        String finalReason = (reason != null && !reason.isBlank()) ? reason : "人工安全审核驳回高危写入操作";

        call.setApprovalStatus(ToolCallApprovalStatus.REJECTED.name());
        call.setApprovedBy(approverId);
        call.setApprovedAt(LocalDateTime.now());
        call.setRejectReason(finalReason);
        call.setExecutionStatus(ToolCallExecutionStatus.SKIPPED.name());
        toolCallMapper.updateById(call);

        transitionRunState(run, RunState.REJECTED);
        run.setOutputSummary("操作被审核员 (#" + approverId + ") 驳回：" + finalReason);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);

        return toRunDto(run, true);
    }

    @Transactional
    public AiRunDTO cancelRun(Long runId) {
        AiRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw new BizException("AI_RUN_NOT_FOUND", "未找到 AI Run: " + runId);
        }
        transitionRunState(run, RunState.CANCELLED);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        return toRunDto(run, true);
    }

    /**
     * 供 Chat 网关记录标准化计量 Run。
     */
    @Transactional
    public String recordCompletedChatRun(
            Long tenantId,
            Long userId,
            String username,
            String providerCode,
            String modelId,
            String promptCode,
            String inputSummary,
            String outputSummary,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String errorType
    ) {
        AiRunEntity run = new AiRunEntity();
        run.setTenantId(tenantId);
        run.setRunNo("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        run.setUserId(userId);
        run.setUsername(username);
        run.setModelId(modelId);
        run.setProviderCode(providerCode);
        run.setPromptCode(promptCode);
        run.setStatus(errorType == null ? RunState.SUCCEEDED.name() : RunState.FAILED.name());
        run.setPromptTokens(promptTokens);
        run.setCompletionTokens(completionTokens);
        run.setTotalTokens(promptTokens + completionTokens);
        BigDecimal cost = meteringQuotaService.recordUsage(
                tenantId, providerCode, modelId, run.getStatus(), promptTokens, completionTokens, latencyMs);
        run.setEstimatedCost(cost);
        run.setLatencyMs(latencyMs);
        run.setErrorType(errorType);
        run.setTraceId(TraceContext.getTraceId());
        run.setInputSummary(truncate(inputSummary, 800));
        run.setOutputSummary(truncate(outputSummary, 2000));
        run.setStartedAt(LocalDateTime.now().minusNanos(Math.max(1L, latencyMs) * 1_000_000L));
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);

        insertStep(run, 1, "MODEL_CHAT", modelId, null, run.getStatus(), promptTokens, completionTokens,
                latencyMs, errorType, Map.of("providerCode", providerCode != null ? providerCode : "gateway"));
        return run.getRunNo();
    }

    private void executeModelStepAndFinish(
            AiRunEntity run,
            int stepNo,
            String modelId,
            AiPromptVersionEntity promptVersion,
            String inputPrompt,
            long startMs
    ) {
        List<ChatMessageData> messages = new ArrayList<>();
        if (promptVersion != null && promptVersion.getSystemPrompt() != null && !promptVersion.getSystemPrompt().isBlank()) {
            messages.add(new ChatMessageData("system", promptVersion.getSystemPrompt()));
        }
        messages.add(new ChatMessageData("user", inputPrompt));

        AiModelConfigEntity modelCfg = modelConfigMapper.selectOne(
                new LambdaQueryWrapper<AiModelConfigEntity>().eq(AiModelConfigEntity::getModelId, modelId).last("LIMIT 1")
        );
        AiProviderEntity provider = modelCfg != null ? providerMapper.selectById(modelCfg.getProviderId()) : null;
        String rawKey = provider != null ? credentialResolver.resolvePlainSecret(provider.getTenantId(), provider.getId(), provider.getApiKey()) : null;
        String providerCode = provider != null ? provider.getProviderCode() : "demo-local";

        if (provider != null && rawKey != null && !rawKey.isBlank()) {
            try {
                String base = provider.getBaseUrl() != null ? provider.getBaseUrl().trim() : "";
                while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                String endpoint = base.endsWith("/chat/completions") ? base : (base + "/chat/completions");

                ChatModelProvider adapter = chatProviders.stream()
                        .filter(p -> p.supports(provider.getProviderType()))
                        .findFirst()
                        .orElseThrow();
                ChatProviderResult result = adapter.chat(new ChatProviderCommand(
                        provider.getProviderType(),
                        providerCode,
                        endpoint,
                        rawKey,
                        modelId,
                        messages,
                        modelCfg.getTemperature() != null ? modelCfg.getTemperature().doubleValue() : 0.7,
                        modelCfg.getMaxTokens(),
                        30
                ));
                run.setProviderCode(providerCode);
                insertStep(run, stepNo, "MODEL_CHAT", modelId, null, "SUCCEEDED",
                        result.usage().promptTokens(), result.usage().completionTokens(), result.latencyMs(),
                        null, Map.of("providerCode", providerCode));
                finishRunSucceeded(run, stepNo + 1, providerCode, modelId, result.content(),
                        result.usage().promptTokens(), result.usage().completionTokens(), startMs);
                return;
            } catch (Exception ex) {
                log.warn("[AiRunOrchestrator] Remote call failed, falling back to local diagnostic reply: {}", ex.getMessage());
            }
        }

        String simulated = "【M3 受控 Run 诊断响应 | 模型 " + modelId + "】已按当前租户 #" + run.getTenantId()
                + " 上下文完成策略校验与计量记录。输入摘要：" + truncate(inputPrompt, 120);
        int pTok = Math.max(16, inputPrompt.length() / 2);
        int cTok = Math.max(24, simulated.length() / 2);
        insertStep(run, stepNo, "MODEL_CHAT", modelId, null, "SUCCEEDED", pTok, cTok,
                System.currentTimeMillis() - startMs, null, Map.of("mode", "offline_simulation"));
        finishRunSucceeded(run, stepNo + 1, providerCode, modelId, simulated, pTok, cTok, startMs);
    }

    private void finishRunSucceeded(
            AiRunEntity run,
            int stepNo,
            String providerCode,
            String modelId,
            String output,
            int promptTokens,
            int completionTokens,
            long startMs
    ) {
        long latency = Math.max(1L, System.currentTimeMillis() - startMs);
        BigDecimal cost = meteringQuotaService.recordUsage(
                run.getTenantId(), providerCode, modelId, "SUCCEEDED", promptTokens, completionTokens, latency);
        BigDecimal safeCost = cost != null ? cost : BigDecimal.ZERO;

        transitionRunState(run, RunState.SUCCEEDED);
        run.setProviderCode(providerCode);
        run.setPromptTokens((run.getPromptTokens() != null ? run.getPromptTokens() : 0) + promptTokens);
        run.setCompletionTokens((run.getCompletionTokens() != null ? run.getCompletionTokens() : 0) + completionTokens);
        run.setTotalTokens(run.getPromptTokens() + run.getCompletionTokens());
        run.setEstimatedCost((run.getEstimatedCost() != null ? run.getEstimatedCost() : BigDecimal.ZERO).add(safeCost));
        run.setLatencyMs(latency);
        run.setOutputSummary(truncate(output, 2000));
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    private void transitionRunState(AiRunEntity run, RunState next) {
        RunState current = RunState.valueOf(run.getStatus());
        run.setStatus(current.transitionTo(next).name());
    }

    private void insertStep(
            AiRunEntity run,
            int stepNo,
            String stepType,
            String modelId,
            String toolCode,
            String status,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String errorType,
            Map<String, Object> detail
    ) {
        AiRunStepEntity step = new AiRunStepEntity();
        step.setTenantId(run.getTenantId());
        step.setRunId(run.getId());
        step.setStepNo(stepNo);
        step.setStepType(stepType);
        step.setModelId(modelId);
        step.setToolCode(toolCode);
        step.setStatus(status);
        step.setPromptTokens(promptTokens);
        step.setCompletionTokens(completionTokens);
        step.setLatencyMs(latencyMs);
        step.setErrorType(errorType);
        step.setDetailJson(toJsonSafe(detail));
        stepMapper.insert(step);
    }

    private boolean hasPermission(AuthPrincipal principal, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return true;
        }
        if (principal == null) {
            return true; // Non-HTTP system unit context
        }
        if (principal.isSuperAdmin()) {
            return true;
        }
        List<String> perms = principal.permissions();
        return perms != null && (perms.contains("*") || perms.contains("*:*:*") || perms.contains(permissionCode));
    }

    private AiRunDTO toRunDto(AiRunEntity r, boolean includeChildren) {
        List<AiRunStepDTO> steps = List.of();
        List<AiToolCallDTO> calls = List.of();
        if (includeChildren) {
            steps = stepMapper.selectList(
                    new LambdaQueryWrapper<AiRunStepEntity>()
                            .eq(AiRunStepEntity::getRunId, r.getId())
                            .orderByAsc(AiRunStepEntity::getStepNo)
            ).stream().map(s -> new AiRunStepDTO(
                    s.getId(), s.getStepNo(), s.getStepType(), s.getModelId(), s.getToolCode(),
                    s.getStatus(), s.getPromptTokens(), s.getCompletionTokens(), s.getLatencyMs(),
                    s.getErrorType(), s.getDetailJson(), s.getCreatedAt()
            )).toList();

            calls = toolCallMapper.selectList(
                    new LambdaQueryWrapper<AiToolCallEntity>()
                            .eq(AiToolCallEntity::getRunId, r.getId())
                            .orderByAsc(AiToolCallEntity::getId)
            ).stream().map(this::toToolCallDto).toList();
        }
        return new AiRunDTO(
                r.getId(), r.getTenantId(), r.getRunNo(), r.getUserId(), r.getUsername(),
                r.getModelId(), r.getProviderCode(), r.getPromptCode(), r.getPromptVersion(),
                r.getStatus(), r.getPromptTokens(), r.getCompletionTokens(), r.getTotalTokens(),
                r.getEstimatedCost(), r.getLatencyMs(), r.getErrorType(), r.getErrorMessage(),
                r.getTraceId(), r.getInputSummary(), r.getOutputSummary(),
                r.getStartedAt(), r.getFinishedAt(), steps, calls
        );
    }

    private AiToolCallDTO toToolCallDto(AiToolCallEntity c) {
        return new AiToolCallDTO(
                c.getId(), c.getRunId(), c.getCallNo(), c.getToolCode(), c.getAdapterType(),
                c.getRiskLevel(), c.getPermissionCode(), c.getIdempotencyKey(),
                c.getArgumentsSummary(), c.getResultSummary(),
                c.getPermissionGranted() != null && c.getPermissionGranted() == 1,
                c.getApprovalStatus(), c.getApprovedBy(), c.getApprovedAt(),
                c.getRejectReason(), c.getExecutionStatus(), c.getLatencyMs(), c.getCreatedAt()
        );
    }

    private String toJsonSafe(Object obj) {
        if (obj == null) return "{}";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
