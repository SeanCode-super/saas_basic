package com.saasbasics.platform.modules.workflow.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceDetailResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessStartRequest;
import com.saasbasics.platform.modules.workflow.service.WorkflowInstanceService;
import com.saasbasics.platform.modules.workflow.service.WorkflowTaskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/instances")
@ConditionalOnProperty(name = "saas.workflow.mode", havingValue = "embedded", matchIfMissing = true)
@RequireTenantFeature("workflow")
public class WorkflowInstanceController {

    private final WorkflowInstanceService instanceService;
    private final WorkflowTaskService taskService;

    public WorkflowInstanceController(WorkflowInstanceService instanceService, WorkflowTaskService taskService) {
        this.instanceService = instanceService;
        this.taskService = taskService;
    }

    @PostMapping("/start")
    @RequirePermission("workflow:instance:write")
    public ApiResponse<ProcessInstanceResponse> start(@Valid @RequestBody ProcessStartRequest request) {
        return ApiResponse.success(instanceService.startInstance(request));
    }

    @GetMapping
    @RequirePermission("workflow:instance:query")
    public ApiResponse<List<ProcessInstanceResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onlyMine
    ) {
        return ApiResponse.success(instanceService.listInstances(category, status, keyword, onlyMine));
    }

    @GetMapping("/{id}")
    @RequirePermission("workflow:instance:query")
    public ApiResponse<ProcessInstanceDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(instanceService.getInstanceDetail(id));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("workflow:instance:write")
    public ApiResponse<Void> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        instanceService.cancelInstance(id, reason);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/urge")
    @RequirePermission("workflow:instance:write")
    public ApiResponse<Void> urge(
            @PathVariable Long id,
            @RequestParam(required = false) String comment
    ) {
        taskService.urgeInstance(id, comment);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/rollback-nodes")
    @RequirePermission("workflow:task:query")
    public ApiResponse<List<Map<String, Object>>> getRollbackNodes(@PathVariable Long id) {
        return ApiResponse.success(taskService.getRollbackNodes(id));
    }
}
