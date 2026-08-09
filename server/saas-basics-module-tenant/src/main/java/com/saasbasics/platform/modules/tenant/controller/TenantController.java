package com.saasbasics.platform.modules.tenant.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSaveRequest;
import com.saasbasics.platform.modules.tenant.dto.TenantStatusUpdateRequest;
import com.saasbasics.platform.modules.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @RequirePermission("tenant:query")
    public ApiResponse<PageResponse<TenantResponse>> page() {
        return ApiResponse.success(tenantService.pageTenants());
    }

    @GetMapping("/{id}")
    @RequirePermission("tenant:query")
    public ApiResponse<TenantResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(tenantService.getTenant(id));
    }

    @PostMapping
    @RequirePermission("tenant:write")
    public ApiResponse<TenantResponse> create(@Valid @RequestBody TenantSaveRequest request) {
        return ApiResponse.success(tenantService.createTenant(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("tenant:write")
    public ApiResponse<TenantResponse> update(@PathVariable Long id, @Valid @RequestBody TenantSaveRequest request) {
        return ApiResponse.success(tenantService.updateTenant(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("tenant:write")
    public ApiResponse<TenantResponse> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody TenantStatusUpdateRequest request) {
        return ApiResponse.success(tenantService.updateStatus(id, request));
    }
}
