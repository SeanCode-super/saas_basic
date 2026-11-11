package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportKeyActivateRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportKeyConfigureRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportPolicyUpdateRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportRoundtripRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportRoundtripResponse;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportSecurityOverviewResponse;
import com.saasbasics.platform.modules.system.service.TransportSecurityService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员传输加密控制台接口：实时查询运行遥测、热更新策略、配置/导入/轮换 X25519 密钥对及多维接口目录统计。
 */
@RestController
@RequestMapping("/api/system/security/transport")
public class TransportSecurityController {

    private final TransportSecurityService transportSecurityService;

    public TransportSecurityController(TransportSecurityService transportSecurityService) {
        this.transportSecurityService = transportSecurityService;
    }

    @GetMapping("/overview")
    @RequirePermission("system:config:query")
    public ApiResponse<TransportSecurityOverviewResponse> overview() {
        return ApiResponse.success(transportSecurityService.getOverview());
    }

    @PutMapping("/policy")
    @RequirePermission("system:config:write")
    public ApiResponse<TransportSecurityOverviewResponse> updatePolicy(
            @Valid @RequestBody TransportPolicyUpdateRequest request) {
        return ApiResponse.success(transportSecurityService.updatePolicy(request));
    }

    @PostMapping("/rotate-key")
    @RequirePermission("system:config:write")
    public ApiResponse<TransportSecurityOverviewResponse> rotateKey() {
        return ApiResponse.success(transportSecurityService.rotateKey());
    }

    @PostMapping("/configure-key")
    @RequirePermission("system:config:write")
    public ApiResponse<TransportSecurityOverviewResponse> configureKey(
            @Valid @RequestBody TransportKeyConfigureRequest request) {
        return ApiResponse.success(transportSecurityService.configureKey(request));
    }

    @PostMapping("/activate-key")
    @RequirePermission("system:config:write")
    public ApiResponse<TransportSecurityOverviewResponse> activateKey(
            @Valid @RequestBody TransportKeyActivateRequest request) {
        return ApiResponse.success(transportSecurityService.activateKey(request));
    }

    @GetMapping("/api-catalog")
    @RequirePermission("system:config:query")
    public ApiResponse<PlatformApiCatalogService.ApiCatalogSnapshot> apiCatalog() {
        return ApiResponse.success(transportSecurityService.getApiCatalog());
    }

    @PostMapping("/verify")
    @RequirePermission("system:config:query")
    public ApiResponse<TransportRoundtripResponse> verifyRoundtrip(
            @RequestBody(required = false) TransportRoundtripRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(transportSecurityService.verifyRoundtrip(request, httpRequest));
    }
}
