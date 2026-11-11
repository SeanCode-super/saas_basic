package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.dto.AiCredentialRefDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiModelConfigDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiModelConfigSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiProviderSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiTestConnectionRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiTestConnectionResult;
import com.saasbasics.platform.modules.ai.application.dto.VaultRotateResultDTO;
import com.saasbasics.platform.modules.ai.application.service.AiModelManageService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/manage")
@RequirePermission("system:config:query")
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

    @GetMapping("/credentials")
    public ApiResponse<List<AiCredentialRefDTO>> listCredentialRefs() {
        return ApiResponse.success(aiModelManageService.listCredentialRefs());
    }

    @PostMapping("/vault/rotate-all")
    public ApiResponse<VaultRotateResultDTO> rotateAllCredentials() {
        return ApiResponse.success(aiModelManageService.rotateAllCredentials());
    }
}
