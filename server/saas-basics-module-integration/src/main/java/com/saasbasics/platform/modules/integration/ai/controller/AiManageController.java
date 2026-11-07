package com.saasbasics.platform.modules.integration.ai.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.modules.integration.ai.dto.*;
import com.saasbasics.platform.modules.integration.ai.service.AiModelManageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/manage")
public class AiManageController {

    private final AiModelManageService aiModelManageService;

    public AiManageController(AiModelManageService aiModelManageService) {
        this.aiModelManageService = aiModelManageService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<AiProviderDTO>> listProviders() {
        return ApiResponse.success(aiModelManageService.listProviders());
    }

    @PostMapping("/providers")
    public ApiResponse<Long> saveProvider(@RequestBody AiProviderSaveRequest request) {
        return ApiResponse.success(aiModelManageService.saveProvider(request));
    }

    @DeleteMapping("/providers/{id}")
    public ApiResponse<Boolean> deleteProvider(@PathVariable Long id) {
        aiModelManageService.deleteProvider(id);
        return ApiResponse.success(true);
    }

    @GetMapping("/models")
    public ApiResponse<List<AiModelConfigDTO>> listModels(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(aiModelManageService.listModels(providerId, status));
    }

    @PostMapping("/models")
    public ApiResponse<Long> saveModel(@RequestBody AiModelConfigSaveRequest request) {
        return ApiResponse.success(aiModelManageService.saveModel(request));
    }

    @DeleteMapping("/models/{id}")
    public ApiResponse<Boolean> deleteModel(@PathVariable Long id) {
        aiModelManageService.deleteModel(id);
        return ApiResponse.success(true);
    }

    @PostMapping("/test-connection")
    public ApiResponse<AiTestConnectionResult> testConnection(@RequestBody AiTestConnectionRequest request) {
        return ApiResponse.success(aiModelManageService.testConnection(request));
    }
}
