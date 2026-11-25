package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowDTO;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowExecuteRequest;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowRunDTO;
import com.saasbasics.platform.modules.ai.application.dto.AgentWorkflowDtos.WorkflowSaveRequest;
import com.saasbasics.platform.modules.ai.application.service.AgentOrchestrationService;
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
@RequestMapping("/api/ai/workflows")
@RequirePermission("system:config:query")
public class AgentWorkflowController {

    private final AgentOrchestrationService orchestrationService;

    public AgentWorkflowController(AgentOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @GetMapping
    public ApiResponse<List<WorkflowDTO>> list() {
        return ApiResponse.success(orchestrationService.listWorkflows());
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowDTO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(orchestrationService.getWorkflow(id));
    }

    @PostMapping
    @RequirePermission("system:config:write")
    public ApiResponse<WorkflowDTO> save(@RequestBody WorkflowSaveRequest request) {
        return ApiResponse.success(orchestrationService.saveWorkflow(request));
    }

    @PostMapping("/{id}/publish")
    @RequirePermission("system:config:write")
    public ApiResponse<WorkflowDTO> publish(@PathVariable("id") Long id) {
        return ApiResponse.success(orchestrationService.publishWorkflow(id));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        orchestrationService.deleteWorkflow(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/execute")
    @RequirePermission("system:config:write")
    public ApiResponse<WorkflowRunDTO> execute(
            @PathVariable("id") Long id,
            @RequestBody(required = false) WorkflowExecuteRequest request) {
        return ApiResponse.success(orchestrationService.executeWorkflow(id, request));
    }

    @PostMapping("/code/{workflowCode}/run")
    @RequirePermission("system:config:write")
    public ApiResponse<WorkflowRunDTO> executeByCode(
            @PathVariable("workflowCode") String workflowCode,
            @RequestBody(required = false) WorkflowExecuteRequest request) {
        return ApiResponse.success(orchestrationService.executeByCode(workflowCode, request));
    }

    @GetMapping("/runs")
    public ApiResponse<List<WorkflowRunDTO>> runs(@RequestParam(name = "workflowId", required = false) Long workflowId) {
        return ApiResponse.success(orchestrationService.listRuns(workflowId));
    }

    @GetMapping("/runs/{runNo}")
    public ApiResponse<WorkflowRunDTO> runDetail(@PathVariable("runNo") String runNo) {
        return ApiResponse.success(orchestrationService.getRunDetail(runNo));
    }
}
