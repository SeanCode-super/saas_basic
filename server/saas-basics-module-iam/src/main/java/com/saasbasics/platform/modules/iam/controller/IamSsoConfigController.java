package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.SsoConfigResponse;
import com.saasbasics.platform.modules.iam.dto.SsoConfigSaveRequest;
import com.saasbasics.platform.modules.iam.service.IamSsoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/sso")
public class IamSsoConfigController {

    private final IamSsoService ssoService;

    public IamSsoConfigController(IamSsoService ssoService) {
        this.ssoService = ssoService;
    }

    @GetMapping("/channels")
    public ApiResponse<List<Map<String, Object>>> listChannels() {
        return ApiResponse.success(ssoService.listAvailableChannels());
    }

    @GetMapping("/configs")
    @RequirePermission("iam:sso:query")
    public ApiResponse<List<SsoConfigResponse>> listConfigs() {
        return ApiResponse.success(ssoService.listConfigs());
    }

    @PostMapping("/configs")
    @RequirePermission("iam:sso:manage")
    public ApiResponse<SsoConfigResponse> saveConfig(@Valid @RequestBody SsoConfigSaveRequest request) {
        return ApiResponse.success(ssoService.saveConfig(request));
    }

    @DeleteMapping("/configs/{id}")
    @RequirePermission("iam:sso:manage")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        ssoService.deleteConfig(id);
        return ApiResponse.success(null);
    }
}
