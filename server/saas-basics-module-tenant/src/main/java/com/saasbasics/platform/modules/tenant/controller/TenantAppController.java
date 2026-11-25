package com.saasbasics.platform.modules.tenant.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.tenant.dto.TenantAppCatalogItemResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppSubscribeRequest;
import com.saasbasics.platform.modules.tenant.service.TenantAppService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/apps")
public class TenantAppController {

    private final TenantAppService tenantAppService;

    public TenantAppController(TenantAppService tenantAppService) {
        this.tenantAppService = tenantAppService;
    }

    @GetMapping("/catalog")
    @RequirePermission("tenant:query")
    public ApiResponse<List<TenantAppCatalogItemResponse>> listCatalog(@RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        return ApiResponse.success(tenantAppService.listAppCatalog(tenantId));
    }

    @PostMapping("/subscribe")
    @RequirePermission("tenant:write")
    public ApiResponse<TenantAppResponse> subscribe(@Valid @RequestBody TenantAppSubscribeRequest request,
                                                    @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        return ApiResponse.success(tenantAppService.subscribeApp(tenantId, request));
    }

    @PutMapping("/extensions/{code}/revision/{revision}")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> upgrade(@PathVariable String code, @PathVariable int revision,
                                     @RequestParam(required = false) Long tenantId) {
        tenantAppService.upgrade(resolveTenantId(tenantId), code, revision);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/status")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") String status,
                                          @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        tenantAppService.updateAppStatus(tenantId, id, status);
        return ApiResponse.success(null);
    }

    public record ToggleAppAuthorizationRequest(
            String appCode,
            boolean enabled,
            String remark
    ) {}

    @PutMapping("/authorize")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> toggleAuthorization(@RequestBody ToggleAppAuthorizationRequest request,
                                                 @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        tenantAppService.toggleAppAuthorization(tenantId, request.appCode(), request.enabled(), request.remark());
        return ApiResponse.success(null);
    }

    public record BatchAuthorizeAppsRequest(
            List<String> appCodes,
            boolean enabled,
            String remark
    ) {}

    @PutMapping("/batch-authorize")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> batchAuthorize(@RequestBody BatchAuthorizeAppsRequest request,
                                            @RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        tenantAppService.batchAuthorizeApps(tenantId, request.appCodes(), request.enabled(), request.remark());
        return ApiResponse.success(null);
    }

    @PostMapping("/reset-package-baseline")
    @RequirePermission("tenant:write")
    public ApiResponse<Void> resetPackageBaseline(@RequestParam(value = "tenantId", required = false) Long queryTenantId) {
        Long tenantId = resolveTenantId(queryTenantId);
        tenantAppService.resetToPackageBaseline(tenantId);
        return ApiResponse.success(null);
    }

    private Long resolveTenantId(Long queryTenantId) {
        AuthPrincipal principal = AuthContext.get();
        Long current = TenantAccessContextHolder.requiredTenantId();
        if (queryTenantId == null || queryTenantId.equals(current)) return current;
        if (principal != null && principal.isPlatformTenant() && principal.isSuperAdmin()
                && "PLATFORM".equalsIgnoreCase(principal.userType())) return queryTenantId;
        throw new com.saasbasics.platform.common.exception.BizException("TENANT_ACCESS_DENIED", "无权管理该租户的应用");
    }
}
