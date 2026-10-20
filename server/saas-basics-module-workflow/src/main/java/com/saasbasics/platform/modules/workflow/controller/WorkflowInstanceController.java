package com.saasbasics.platform.modules.workflow.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceDetailResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessInstanceResponse;
import com.saasbasics.platform.modules.workflow.dto.ProcessStartRequest;
import com.saasbasics.platform.modules.workflow.service.WorkflowInstanceService;
import jakarta.validation.Valid;
import java.util.List;
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
public class WorkflowInstanceController {

    private final WorkflowInstanceService instanceService;

    public WorkflowInstanceController(WorkflowInstanceService instanceService) {
        this.instanceService = instanceService;
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
}
