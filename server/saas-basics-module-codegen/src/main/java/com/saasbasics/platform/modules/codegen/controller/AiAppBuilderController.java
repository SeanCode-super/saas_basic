package com.saasbasics.platform.modules.codegen.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.modules.codegen.dto.AiAppDesignResponse;
import com.saasbasics.platform.modules.codegen.dto.AiAppPromptRequest;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelPublishRequest;
import com.saasbasics.platform.modules.codegen.dto.DynamicModelResponse;
import com.saasbasics.platform.modules.codegen.service.AiAppBuilderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/codegen/ai-builder")
@RequireTenantFeature("codegen")
public class AiAppBuilderController {

    private final AiAppBuilderService aiAppBuilderService;

    public AiAppBuilderController(AiAppBuilderService aiAppBuilderService) {
        this.aiAppBuilderService = aiAppBuilderService;
    }

    @PostMapping({"/design", "/prompt-design"})
    public ApiResponse<AiAppDesignResponse> designApp(@Valid @RequestBody AiAppPromptRequest request) {
        return ApiResponse.success(aiAppBuilderService.designApp(request));
    }

    @PostMapping("/publish")
    public ApiResponse<DynamicModelResponse> publishApp(@Valid @RequestBody DynamicModelPublishRequest request) {
        return ApiResponse.success(aiAppBuilderService.publishApp(request));
    }

    @GetMapping("/models")
    public ApiResponse<List<DynamicModelResponse>> listModels() {
        return ApiResponse.success(aiAppBuilderService.listPublishedModels());
    }

    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable("id") Long id) {
        aiAppBuilderService.deleteModel(id);
        return ApiResponse.success(null);
    }
}
