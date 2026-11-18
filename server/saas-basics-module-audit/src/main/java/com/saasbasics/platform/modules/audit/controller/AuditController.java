package com.saasbasics.platform.modules.audit.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.modules.audit.dto.AuditOverviewResponse;
import com.saasbasics.platform.modules.audit.dto.BlacklistCreateRequest;
import com.saasbasics.platform.modules.audit.dto.BlacklistItemResponse;
import com.saasbasics.platform.modules.audit.dto.HighRiskActionItemResponse;
import com.saasbasics.platform.modules.audit.dto.LoginLogResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogResponse;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateRequest;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateResponse;
import com.saasbasics.platform.modules.audit.dto.RiskRuleResponse;
import com.saasbasics.platform.modules.audit.dto.RiskRuleStatusRequest;
import com.saasbasics.platform.modules.audit.service.AuditService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequireTenantFeature("audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    @RequirePermission("audit:operation:query")
    public ApiResponse<AuditOverviewResponse> overview() {
        return ApiResponse.success(auditService.overview());
    }

    @GetMapping("/login-logs")
    @RequirePermission("audit:operation:query")
    public ApiResponse<List<LoginLogResponse>> loginLogs(@RequestParam(required = false) String username,
                                                         @RequestParam(required = false) Boolean success) {
        return ApiResponse.success(auditService.listLoginLogs(username, success));
    }

    @GetMapping("/operation-logs")
    @RequirePermission("audit:operation:query")
    public ApiResponse<List<OperationLogResponse>> operationLogs(@RequestParam(required = false) String bizModule,
                                                                 @RequestParam(required = false) String operationType) {
        return ApiResponse.success(auditService.listOperationLogs(bizModule, operationType));
    }

    @GetMapping("/operation-logs/{id}/diff")
    @RequirePermission("audit:operation:query")
    public ApiResponse<OperationLogDiffResponse> operationLogDiff(@PathVariable Long id) {
        return ApiResponse.success(auditService.getOperationLogDiff(id));
    }

    @GetMapping("/blacklist")
    @RequirePermission("audit:operation:query")
    public ApiResponse<List<BlacklistItemResponse>> blacklist() {
        return ApiResponse.success(auditService.listBlacklist());
    }

    @PostMapping("/blacklist")
    @RequirePermission("audit:security:write")
    public ApiResponse<BlacklistItemResponse> addBlacklist(@Valid @RequestBody BlacklistCreateRequest request) {
        return ApiResponse.success(auditService.addBlacklist(request));
    }

    @DeleteMapping("/blacklist/{id}")
    @RequirePermission("audit:security:write")
    public ApiResponse<Void> removeBlacklist(@PathVariable Long id) {
        auditService.removeBlacklist(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/risk-rules")
    @RequirePermission("audit:operation:query")
    public ApiResponse<List<RiskRuleResponse>> riskRules() {
        return ApiResponse.success(auditService.listRiskRules());
    }

    @PatchMapping("/risk-rules/{id}/status")
    @RequirePermission("audit:security:write")
    public ApiResponse<RiskRuleResponse> updateRiskRuleStatus(@PathVariable Long id,
                                                              @RequestBody RiskRuleStatusRequest request) {
        return ApiResponse.success(auditService.updateRiskRuleStatus(id, request.enabled()));
    }

    @PostMapping("/risk-rules/evaluate")
    @RequirePermission("audit:operation:query")
    public ApiResponse<RiskEvaluateResponse> evaluateRiskRule(@Valid @RequestBody RiskEvaluateRequest request) {
        return ApiResponse.success(auditService.evaluateRiskRule(request));
    }

    @GetMapping("/high-risk-catalog")
    @RequirePermission("audit:operation:query")
    public ApiResponse<List<HighRiskActionItemResponse>> highRiskCatalog() {
        return ApiResponse.success(auditService.listHighRiskCatalog());
    }
}
