package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.PortalClientSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalEntryResponse;
import com.saasbasics.platform.modules.iam.dto.PortalClientResponse;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PortalTerminalResponse;
import com.saasbasics.platform.modules.iam.service.PortalEntryService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalEntryService portalEntryService;

    public PortalController(PortalEntryService portalEntryService) {
        this.portalEntryService = portalEntryService;
    }

    @GetMapping("/entry")
    public ApiResponse<PortalEntryResponse> entry(@RequestParam(required = false) String clientId,
                                                  @RequestParam(required = false) String terminalCode,
                                                  @RequestParam(required = false) String tenantCode,
                                                  HttpServletRequest request) {
        String effectiveTenantCode = tenantCode;
        if (effectiveTenantCode == null || effectiveTenantCode.isBlank()) {
            effectiveTenantCode = resolveTenantFromRequest(request);
        }
        return ApiResponse.success(portalEntryService.resolveEntry(clientId, terminalCode, effectiveTenantCode));
    }

    private String resolveTenantFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String headerTenant = request.getHeader("X-Tenant-Code");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        if (host == null || host.isBlank()) {
            return null;
        }
        if (host.contains(":")) {
            host = host.substring(0, host.indexOf(":"));
        }
        host = host.toLowerCase().trim();
        if (host.equals("localhost") || host.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            String sub = parts[0];
            if ("platform".equals(sub) || "admin".equals(sub)) {
                return "platform";
            }
            if (!List.of("localhost", "127", "www", "api", "static", "cdn").contains(sub)) {
                return sub;
            }
        }
        return null;
    }

    @GetMapping("/clients")
    @RequirePermission("system:config:query")
    public ApiResponse<List<PortalClientResponse>> clients() {
        return ApiResponse.success(portalEntryService.listClients());
    }

    @PostMapping("/clients")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalClientResponse> createClient(@Valid @RequestBody PortalClientSaveRequest request) {
        return ApiResponse.success(portalEntryService.createClient(request));
    }

    @PutMapping("/clients/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalClientResponse> updateClient(@PathVariable Long id,
                                                          @Valid @RequestBody PortalClientSaveRequest request) {
        return ApiResponse.success(portalEntryService.updateClient(id, request));
    }

    @GetMapping("/terminals")
    @RequirePermission("system:config:query")
    public ApiResponse<List<PortalTerminalResponse>> terminals(@RequestParam(required = false) String clientId) {
        return ApiResponse.success(portalEntryService.listTerminals(clientId));
    }

    @PostMapping("/terminals")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalTerminalResponse> createTerminal(@Valid @RequestBody PortalTerminalSaveRequest request) {
        return ApiResponse.success(portalEntryService.createTerminal(request));
    }

    @PutMapping("/terminals/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<PortalTerminalResponse> updateTerminal(@PathVariable Long id,
                                                              @Valid @RequestBody PortalTerminalSaveRequest request) {
        return ApiResponse.success(portalEntryService.updateTerminal(id, request));
    }
}
