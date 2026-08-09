package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.dto.RoleSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission("iam:role:query")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    @RequirePermission("iam:role:query")
    public ApiResponse<RoleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(roleService.getRole(id));
    }

    @PostMapping
    @RequirePermission("iam:role:write")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("iam:role:write")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.updateRole(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("iam:role:write")
    public ApiResponse<RoleResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(roleService.updateStatus(id, request));
    }
}
