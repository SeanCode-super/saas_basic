package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.integration.dto.OpenAppAuthContext;
import com.saasbasics.platform.modules.integration.service.PlatOpenAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/api/v1")
public class OpenApiV1Controller {

    private final PlatOpenAppService openAppService;

    public OpenApiV1Controller(PlatOpenAppService openAppService) {
        this.openAppService = openAppService;
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
                "serverTime", LocalDateTime.now()
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
                "acknowledgedAt", LocalDateTime.now()
        ));
    }
}
