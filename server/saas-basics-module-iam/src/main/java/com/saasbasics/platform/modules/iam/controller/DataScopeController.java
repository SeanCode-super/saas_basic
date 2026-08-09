package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.DataScopeResponse;
import com.saasbasics.platform.modules.iam.dto.DataScopeSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.DataScopeService;
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
@RequestMapping("/api/iam/data-scopes")
public class DataScopeController {

    private final DataScopeService dataScopeService;

    public DataScopeController(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @GetMapping
    @RequirePermission("iam:data-scope:query")
    public ApiResponse<List<DataScopeResponse>> list() {
        return ApiResponse.success(dataScopeService.listDataScopes());
    }

    @GetMapping("/{id}")
    @RequirePermission("iam:data-scope:query")
    public ApiResponse<DataScopeResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(dataScopeService.getDataScope(id));
    }

    @PostMapping
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataScopeResponse> create(@Valid @RequestBody DataScopeSaveRequest request) {
        return ApiResponse.success(dataScopeService.createDataScope(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataScopeResponse> update(@PathVariable Long id, @Valid @RequestBody DataScopeSaveRequest request) {
        return ApiResponse.success(dataScopeService.updateDataScope(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("iam:data-scope:write")
    public ApiResponse<DataScopeResponse> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(dataScopeService.updateStatus(id, request));
    }
}
