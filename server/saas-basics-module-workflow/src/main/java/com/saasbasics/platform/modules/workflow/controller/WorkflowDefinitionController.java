package com.saasbasics.platform.modules.workflow.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessDefinitionSaveRequest;
import com.saasbasics.platform.modules.workflow.service.WorkflowDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/definitions")
@ConditionalOnProperty(name = "saas.workflow.mode", havingValue = "embedded", matchIfMissing = true)
@RequireTenantFeature("workflow")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @GetMapping
    @RequirePermission("workflow:definition:query")
    public ApiResponse<List<ProcessDefinitionResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(definitionService.listDefinitions(category, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("workflow:definition:query")
    public ApiResponse<ProcessDefinitionResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(definitionService.getDefinition(id));
    }

    @PostMapping
    @RequirePermission("workflow:definition:write")
    public ApiResponse<ProcessDefinitionResponse> create(@Valid @RequestBody ProcessDefinitionSaveRequest request) {
        return ApiResponse.success(definitionService.createDefinition(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("workflow:definition:write")
    public ApiResponse<ProcessDefinitionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProcessDefinitionSaveRequest request
    ) {
        return ApiResponse.success(definitionService.updateDefinition(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("workflow:definition:write")
    public ApiResponse<ProcessDefinitionResponse> toggleStatus(@PathVariable Long id) {
        return ApiResponse.success(definitionService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("workflow:definition:write")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        definitionService.deleteDefinition(id);
        return ApiResponse.success(null);
    }
}
