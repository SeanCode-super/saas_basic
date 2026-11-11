package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpProbeResultDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpServerDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiMcpServerSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPromptVersionDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiRunCreateRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiRunDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiTenantQuotaDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiTenantQuotaSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiToolApprovalRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiToolCallDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiToolDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiToolWhitelistRequest;
import com.saasbasics.platform.modules.ai.application.service.AiMcpGovernanceService;
import com.saasbasics.platform.modules.ai.application.service.AiMeteringQuotaService;
import com.saasbasics.platform.modules.ai.application.service.AiPromptService;
import com.saasbasics.platform.modules.ai.application.service.AiRunOrchestratorService;
import com.saasbasics.platform.ratelimit.RateLimit;
import com.saasbasics.platform.ratelimit.RateLimitType;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M3 企业级 AI 治理与运行面控制器（Prompts / MCP / 工具白名单 / Token 配额 / Run 状态机与审批）。
 */
@RestController
@RequestMapping("/api/ai")
@RequirePermission("system:config:query")
public class AiGovernanceController {

    private final AiPromptService promptService;
    private final AiMcpGovernanceService mcpGovernanceService;
    private final AiMeteringQuotaService meteringQuotaService;
    private final AiRunOrchestratorService runOrchestratorService;

    public AiGovernanceController(
            AiPromptService promptService,
            AiMcpGovernanceService mcpGovernanceService,
            AiMeteringQuotaService meteringQuotaService,
            AiRunOrchestratorService runOrchestratorService
    ) {
        this.promptService = promptService;
        this.mcpGovernanceService = mcpGovernanceService;
        this.meteringQuotaService = meteringQuotaService;
        this.runOrchestratorService = runOrchestratorService;
    }

    // ---------- Prompts & Versioning ----------
    @GetMapping("/prompts")
    public ApiResponse<List<AiPromptDTO>> listPrompts() {
        return ApiResponse.success(promptService.listPrompts());
    }

    @GetMapping("/prompts/{promptCode}/versions")
    public ApiResponse<List<AiPromptVersionDTO>> listPromptVersions(@PathVariable String promptCode) {
        return ApiResponse.success(promptService.listVersions(promptCode));
    }

    @PostMapping("/prompts")
    public ApiResponse<AiPromptVersionDTO> savePromptVersion(@RequestBody AiPromptSaveRequest request) {
        return ApiResponse.success(promptService.saveNewVersion(request));
    }

    @PostMapping("/prompts/{promptCode}/publish/{versionNo}")
    public ApiResponse<AiPromptDTO> publishPromptVersion(
            @PathVariable String promptCode,
            @PathVariable int versionNo) {
        return ApiResponse.success(promptService.publishVersion(promptCode, versionNo));
    }

    // ---------- MCP Servers & Tool Whitelist Governance ----------
    @GetMapping("/mcp-servers")
    public ApiResponse<List<AiMcpServerDTO>> listMcpServers() {
        return ApiResponse.success(mcpGovernanceService.listMcpServers());
    }

    @PostMapping("/mcp-servers")
    public ApiResponse<Long> saveMcpServer(@RequestBody AiMcpServerSaveRequest request) {
        return ApiResponse.success(mcpGovernanceService.saveMcpServer(request));
    }

    @DeleteMapping("/mcp-servers/{id}")
    public ApiResponse<Boolean> deleteMcpServer(@PathVariable Long id) {
        mcpGovernanceService.deleteMcpServer(id);
        return ApiResponse.success(true);
    }

    @PostMapping("/mcp-servers/{id}/probe")
    public ApiResponse<AiMcpProbeResultDTO> probeMcpServer(@PathVariable Long id) {
        return ApiResponse.success(mcpGovernanceService.probeAndSyncTools(id));
    }

    @GetMapping("/tools")
    public ApiResponse<List<AiToolDTO>> listTools() {
        return ApiResponse.success(mcpGovernanceService.listTools());
    }

    @PutMapping("/tools/{id}/whitelist")
    public ApiResponse<AiToolDTO> updateToolGovernance(
            @PathVariable Long id,
            @RequestBody AiToolWhitelistRequest request) {
        return ApiResponse.success(mcpGovernanceService.updateToolGovernance(id, request));
    }

    // ---------- Tenant Token Quota & Billing ----------
    @GetMapping("/quota")
    public ApiResponse<AiTenantQuotaDTO> getQuota() {
        return ApiResponse.success(meteringQuotaService.getCurrentQuota());
    }

    @PutMapping("/quota")
    public ApiResponse<AiTenantQuotaDTO> updateQuota(@RequestBody AiTenantQuotaSaveRequest request) {
        return ApiResponse.success(meteringQuotaService.updateQuota(request));
    }

    // ---------- AI Runs & Human Approval State Machine ----------
    @GetMapping("/runs")
    public ApiResponse<List<AiRunDTO>> listRuns() {
        return ApiResponse.success(runOrchestratorService.listRuns());
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<AiRunDTO> getRun(@PathVariable Long runId) {
        return ApiResponse.success(runOrchestratorService.getRunDetail(runId));
    }

    @PostMapping("/runs")
    @RateLimit(type = RateLimitType.TENANT, permits = 30, windowSeconds = 60,
            message = "AI Run 编排触发频次超限，请稍后再试")
    public ApiResponse<AiRunDTO> createRun(@RequestBody AiRunCreateRequest request) {
        return ApiResponse.success(runOrchestratorService.executeRun(request));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<AiRunDTO> cancelRun(@PathVariable Long runId) {
        return ApiResponse.success(runOrchestratorService.cancelRun(runId));
    }

    @GetMapping("/tool-calls/pending")
    public ApiResponse<List<AiToolCallDTO>> listPendingApprovals() {
        return ApiResponse.success(runOrchestratorService.listPendingApprovals());
    }

    @PostMapping("/tool-calls/{callId}/approve")
    public ApiResponse<AiRunDTO> approveToolCall(
            @PathVariable Long callId,
            @RequestBody(required = false) AiToolApprovalRequest request) {
        return ApiResponse.success(runOrchestratorService.approveToolCall(
                callId, request != null ? request.reason() : null));
    }

    @PostMapping("/tool-calls/{callId}/reject")
    public ApiResponse<AiRunDTO> rejectToolCall(
            @PathVariable Long callId,
            @RequestBody(required = false) AiToolApprovalRequest request) {
        return ApiResponse.success(runOrchestratorService.rejectToolCall(
                callId, request != null ? request.reason() : null));
    }
}
