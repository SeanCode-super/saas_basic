package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.crm.dto.CrmCustomerResponse;
import com.saasbasics.platform.modules.crm.service.CrmService;
import com.saasbasics.platform.modules.erp.dto.ErpProductResponse;
import com.saasbasics.platform.modules.erp.service.ErpService;
import com.saasbasics.platform.modules.integration.dto.OpenAppAuthContext;
import com.saasbasics.platform.modules.integration.service.PlatOpenAppService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/api/v1")
public class OpenApiV1Controller {

    private final PlatOpenAppService openAppService;
    private final CrmService crmService;
    private final ErpService erpService;

    public OpenApiV1Controller(PlatOpenAppService openAppService) {
        this(openAppService, null, null);
    }

    @Autowired
    public OpenApiV1Controller(
            PlatOpenAppService openAppService,
            CrmService crmService,
            ErpService erpService
    ) {
        this.openAppService = openAppService;
        this.crmService = crmService;
        this.erpService = erpService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        OpenAppAuthContext app = openAppService.authenticateOpenApi(appKey, timestamp, nonce, signature);

        return ApiResponse.success(Map.of(
                "status", "UP",
                "appName", app.appName(),
                "tenantId", app.tenantId(),
                "rateLimitQps", app.rateLimit(),
                "serverTime", Instant.now().toString()
        ));
    }

    @PostMapping("/echo")
    public ApiResponse<Map<String, Object>> echo(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {

        OpenAppAuthContext app = openAppService.authenticateOpenApi(appKey, timestamp, nonce, signature);

        return ApiResponse.success(Map.of(
                "echo", body != null ? body : Map.of(),
                "callerAppKey", app.appKey(),
                "tenantId", app.tenantId(),
                "acknowledgedAt", Instant.now().toString()
        ));
    }

    @GetMapping("/crm/customers")
    public ApiResponse<List<CrmCustomerResponse>> listCrmCustomers(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestParam(required = false) Integer isPool,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level) {

        OpenAppAuthContext app = openAppService.authenticateOpenApi(appKey, timestamp, nonce, signature);
        openAppService.requireScope(app, "open:crm:read");
        return ApiResponse.success(executeAsOpenApp(app, () ->
                crmService != null ? crmService.listCustomers(isPool, keyword, level) : List.of()));
    }

    @GetMapping("/erp/products")
    public ApiResponse<List<ErpProductResponse>> listErpProducts(
            @RequestHeader(value = "X-App-Key", required = false) String appKey,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        OpenAppAuthContext app = openAppService.authenticateOpenApi(appKey, timestamp, nonce, signature);
        openAppService.requireScope(app, "open:erp:read");
        return ApiResponse.success(executeAsOpenApp(app, () ->
                erpService != null ? erpService.listProducts() : List.of()));
    }

    private <T> T executeAsOpenApp(OpenAppAuthContext app, Supplier<T> action) {
        List<String> scopes = app.allowedPermissions() != null
                ? Arrays.stream(app.allowedPermissions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        AuthPrincipal openPrincipal = new AuthPrincipal(
                0L,
                app.tenantId(),
                "open-tenant-" + app.tenantId(),
                0L,
                app.appKey(),
                app.appName(),
                "OPEN_APP",
                "ACTIVE",
                LocalDateTime.now().plusMinutes(5),
                scopes,
                List.of(),
                List.of(),
                List.of()
        );
        AuthPrincipal previous = AuthContext.get();
        AuthContext.set(openPrincipal);
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                app.tenantId(), "open-tenant-" + app.tenantId(), 0L)) {
            return action.get();
        } finally {
            if (previous != null) {
                AuthContext.set(previous);
            } else {
                AuthContext.clear();
            }
        }
    }
}
