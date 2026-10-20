package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.integration.dto.OpenAppResponse;
import com.saasbasics.platform.modules.integration.dto.OpenAppSaveRequest;
import com.saasbasics.platform.modules.integration.service.PlatOpenAppService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/open-apps")
public class OpenPlatformController {

    private final PlatOpenAppService openAppService;

    public OpenPlatformController(PlatOpenAppService openAppService) {
        this.openAppService = openAppService;
    }

    @GetMapping
    public ApiResponse<List<OpenAppResponse>> listApps() {
        return ApiResponse.success(openAppService.listApps());
    }

    @PostMapping
    public ApiResponse<OpenAppResponse> saveApp(@Valid @RequestBody OpenAppSaveRequest request) {
        return ApiResponse.success(openAppService.saveApp(request));
    }

    @PostMapping("/{id}/regenerate-secret")
    public ApiResponse<OpenAppResponse> regenerateSecret(@PathVariable Long id) {
        return ApiResponse.success(openAppService.regenerateSecret(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteApp(@PathVariable Long id) {
        openAppService.deleteApp(id);
        return ApiResponse.success(null);
    }
}
