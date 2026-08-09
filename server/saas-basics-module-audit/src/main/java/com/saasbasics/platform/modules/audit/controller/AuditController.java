package com.saasbasics.platform.modules.audit.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.audit.dto.AuditOverviewResponse;
import com.saasbasics.platform.modules.audit.dto.LoginLogResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogResponse;
import com.saasbasics.platform.modules.audit.service.AuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
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
}
