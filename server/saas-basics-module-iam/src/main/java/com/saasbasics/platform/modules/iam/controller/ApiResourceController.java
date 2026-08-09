package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.ApiResourceResponse;
import com.saasbasics.platform.modules.iam.dto.ApiResourceSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.ApiResourceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/api-resources")
public class ApiResourceController {

    private final ApiResourceService apiResourceService;

    public ApiResourceController(ApiResourceService apiResourceService) {
        this.apiResourceService = apiResourceService;
    }

    @GetMapping
    @RequirePermission("iam:api-resource:query")
    public ApiResponse<List<ApiResourceResponse>> list() {
        return ApiResponse.success(apiResourceService.listApiResources());
    }

    @GetMapping("/{id}")
    @RequirePermission("iam:api-resource:query")
    public ApiResponse<ApiResourceResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(apiResourceService.getApiResource(id));
    }

    @PostMapping
    @RequirePermission("iam:api-resource:write")
    public ApiResponse<ApiResourceResponse> create(@Valid @RequestBody ApiResourceSaveRequest request) {
        return ApiResponse.success(apiResourceService.createApiResource(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("iam:api-resource:write")
    public ApiResponse<ApiResourceResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody ApiResourceSaveRequest request) {
        return ApiResponse.success(apiResourceService.updateApiResource(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("iam:api-resource:write")
    public ApiResponse<ApiResourceResponse> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(apiResourceService.updateStatus(id, request));
    }
}
