package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.RoleApiAssignmentRequest;
import com.saasbasics.platform.modules.iam.dto.RoleApiAssignmentResponse;
import com.saasbasics.platform.modules.iam.dto.UserRoleAssignmentRequest;
import com.saasbasics.platform.modules.iam.dto.UserRoleAssignmentResponse;
import com.saasbasics.platform.modules.iam.service.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping("/users/{userId}/roles")
    @RequirePermission("iam:user-role:query")
    public ApiResponse<UserRoleAssignmentResponse> userRoles(@PathVariable Long userId,
                                                             @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(authorizationService.getUserRoleAssignments(userId, resolveTenantId(tenantId)));
    }

    @PutMapping("/users/{userId}/roles")
    @RequirePermission("iam:user-role:write")
    public ApiResponse<UserRoleAssignmentResponse> assignUserRoles(@PathVariable Long userId,
                                                                   @Valid @RequestBody UserRoleAssignmentRequest request) {
        return ApiResponse.success(authorizationService.assignUserRoles(userId, request));
    }

    @GetMapping("/roles/{roleId}/api-resources")
    @RequirePermission("iam:role-api:query")
    public ApiResponse<RoleApiAssignmentResponse> roleApis(@PathVariable Long roleId,
                                                           @RequestParam(required = false) Long tenantId) {
        return ApiResponse.success(authorizationService.getRoleApiAssignments(roleId, resolveTenantId(tenantId)));
    }

    @PutMapping("/roles/{roleId}/api-resources")
    @RequirePermission("iam:role-api:write")
    public ApiResponse<RoleApiAssignmentResponse> assignRoleApis(@PathVariable Long roleId,
                                                                 @Valid @RequestBody RoleApiAssignmentRequest request) {
        return ApiResponse.success(authorizationService.assignRoleApis(roleId, request));
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
