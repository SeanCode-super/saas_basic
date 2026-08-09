package com.saasbasics.platform.modules.system.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.system.dto.SystemConfigResponse;
import com.saasbasics.platform.modules.system.dto.SystemConfigSaveRequest;
import com.saasbasics.platform.modules.system.service.SystemConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/configs")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    @RequirePermission("system:config:query")
    public ApiResponse<List<SystemConfigResponse>> list() {
        return ApiResponse.success(systemConfigService.listConfigs());
    }

    @GetMapping("/{id}")
    @RequirePermission("system:config:query")
    public ApiResponse<SystemConfigResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(systemConfigService.getConfig(id));
    }

    @PostMapping
    @RequirePermission("system:config:write")
    public ApiResponse<SystemConfigResponse> create(@Valid @RequestBody SystemConfigSaveRequest request) {
        return ApiResponse.success(systemConfigService.createConfig(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<SystemConfigResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody SystemConfigSaveRequest request) {
        return ApiResponse.success(systemConfigService.updateConfig(id, request));
    }
}
