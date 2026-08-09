package com.saasbasics.platform.modules.health.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping("/ready")
    public ApiResponse<Map<String, String>> ready() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "tenantCode", TenantContext.getTenantCode()
        ));
    }
}
