package com.saasbasics.platform.modules.tenant.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageSaveRequest;
import com.saasbasics.platform.modules.tenant.service.TenantPackageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant-packages")
public class TenantPackageController {

    private final TenantPackageService packageService;

    public TenantPackageController(TenantPackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    @RequirePermission("tenant:query")
    public ApiResponse<List<TenantPackageResponse>> list() {
        return ApiResponse.success(packageService.listPackages());
    }

    @GetMapping("/{id}")
    @RequirePermission("tenant:query")
    public ApiResponse<TenantPackageResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(packageService.getPackage(id));
    }

    @PostMapping
    @RequirePermission("tenant:write")
    public ApiResponse<TenantPackageResponse> create(@Valid @RequestBody TenantPackageSaveRequest request) {
        return ApiResponse.success(packageService.createPackage(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("tenant:write")
    public ApiResponse<TenantPackageResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody TenantPackageSaveRequest request) {
        return ApiResponse.success(packageService.updatePackage(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ApiResponse.success(null);
    }
}
