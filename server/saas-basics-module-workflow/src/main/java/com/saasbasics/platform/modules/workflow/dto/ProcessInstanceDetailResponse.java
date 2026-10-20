package com.saasbasics.platform.modules.workflow.dto;

import java.util.List;

public record ProcessInstanceDetailResponse(
        ProcessInstanceResponse instance,
        ProcessDefinitionResponse definition,
        String formDataJson,
        List<WorkflowTaskResponse> tasks,
        List<TaskLogResponse> logs
) {
}
