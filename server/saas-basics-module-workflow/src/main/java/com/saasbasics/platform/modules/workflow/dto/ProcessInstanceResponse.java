package com.saasbasics.platform.modules.workflow.dto;

import java.time.LocalDateTime;

public record ProcessInstanceResponse(
        Long id,
        Long tenantId,
        String instanceNo,
        Long definitionId,
        String processCode,
        String processName,
        String title,
        String category,
        Long initiatorId,
        String initiatorName,
        Long initiatorDeptId,
        String initiatorDeptName,
        String currentNodeId,
        String currentNodeName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
}
