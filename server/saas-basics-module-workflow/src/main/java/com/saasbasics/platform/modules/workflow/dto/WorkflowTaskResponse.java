package com.saasbasics.platform.modules.workflow.dto;

import java.time.LocalDateTime;

public record WorkflowTaskResponse(
        Long id,
        Long tenantId,
        Long instanceId,
        String instanceNo,
        String processCode,
        String processName,
        String instanceTitle,
        String category,
        Long initiatorId,
        String initiatorName,
        String nodeId,
        String nodeName,
        String nodeType,
        Long assigneeId,
        String assigneeName,
        String status,
        String action,
        String comment,
        LocalDateTime claimTime,
        LocalDateTime finishTime,
        LocalDateTime createdAt
) {
}
