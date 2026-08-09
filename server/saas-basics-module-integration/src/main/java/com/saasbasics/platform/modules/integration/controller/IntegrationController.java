package com.saasbasics.platform.modules.integration.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.integration.dto.DatasourceResponse;
import com.saasbasics.platform.modules.integration.dto.DatasourceSaveRequest;
import com.saasbasics.platform.modules.integration.service.IntegrationService;
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
@RequestMapping("/api/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/datasources")
    @RequirePermission("integration:datasource:query")
    public ApiResponse<List<DatasourceResponse>> datasources() {
        return ApiResponse.success(integrationService.listDatasources());
    }

    @GetMapping("/datasources/{id}")
    @RequirePermission("integration:datasource:query")
    public ApiResponse<DatasourceResponse> datasource(@PathVariable Long id) {
        return ApiResponse.success(integrationService.getDatasource(id));
    }

    @PostMapping("/datasources")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<DatasourceResponse> createDatasource(@Valid @RequestBody DatasourceSaveRequest request) {
        return ApiResponse.success(integrationService.createDatasource(request));
    }

    @PutMapping("/datasources/{id}")
    @RequirePermission("integration:datasource:write")
    public ApiResponse<DatasourceResponse> updateDatasource(@PathVariable Long id,
                                                            @Valid @RequestBody DatasourceSaveRequest request) {
        return ApiResponse.success(integrationService.updateDatasource(id, request));
    }
}
