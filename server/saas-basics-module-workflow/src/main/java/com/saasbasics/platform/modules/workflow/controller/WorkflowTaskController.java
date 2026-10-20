package com.saasbasics.platform.modules.workflow.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.workflow.dto.TaskHandleRequest;
import com.saasbasics.platform.modules.workflow.dto.WorkflowTaskResponse;
import com.saasbasics.platform.modules.workflow.service.WorkflowTaskService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/tasks")
@ConditionalOnProperty(name = "saas.workflow.mode", havingValue = "embedded", matchIfMissing = true)
public class WorkflowTaskController {

    private final WorkflowTaskService taskService;

    public WorkflowTaskController(WorkflowTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/pending")
    @RequirePermission("workflow:task:query")
    public ApiResponse<List<WorkflowTaskResponse>> listMyPending() {
        return ApiResponse.success(taskService.listMyPendingTasks());
    }

    @GetMapping("/completed")
    @RequirePermission("workflow:task:query")
    public ApiResponse<List<WorkflowTaskResponse>> listMyCompleted() {
        return ApiResponse.success(taskService.listMyCompletedTasks());
    }

    @GetMapping("/cc")
    @RequirePermission("workflow:task:query")
    public ApiResponse<List<WorkflowTaskResponse>> listCc() {
        return ApiResponse.success(taskService.listCcTasks());
    }

    @PostMapping("/{id}/handle")
    @RequirePermission("workflow:task:write")
    public ApiResponse<Void> handle(
            @PathVariable Long id,
            @RequestBody TaskHandleRequest request
    ) {
        taskService.handleTask(id, request);
        return ApiResponse.success(null);
    }
}
