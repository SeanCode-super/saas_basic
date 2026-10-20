package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.PermissionSimulationRequest;
import com.saasbasics.platform.modules.iam.dto.PermissionSimulationResponse;
import com.saasbasics.platform.modules.iam.dto.UserPermissionInspectionResponse;
import com.saasbasics.platform.modules.iam.service.PermissionInspectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/inspect")
public class PermissionInspectionController {

    private final PermissionInspectionService permissionInspectionService;

    public PermissionInspectionController(PermissionInspectionService permissionInspectionService) {
        this.permissionInspectionService = permissionInspectionService;
    }

    @GetMapping("/users/{userId}")
    @RequirePermission("iam:user:query")
    public ApiResponse<UserPermissionInspectionResponse> inspectUser(@PathVariable Long userId,
                                                                     @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(permissionInspectionService.inspect(resolveTenantId(tenantId), userId));
    }

    @PostMapping("/simulate")
    @RequirePermission("iam:user:query")
    public ApiResponse<PermissionSimulationResponse> simulate(@Valid @RequestBody PermissionSimulationRequest request,
                                                              @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(permissionInspectionService.simulate(resolveTenantId(tenantId), request));
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        if (AuthContext.get() != null && AuthContext.get().tenantId() != null) {
            return AuthContext.get().tenantId();
        }
        throw new IllegalArgumentException("tenantId is required");
    }
}
