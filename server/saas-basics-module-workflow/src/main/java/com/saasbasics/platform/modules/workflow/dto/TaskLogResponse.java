package com.saasbasics.platform.modules.workflow.dto;

import java.time.LocalDateTime;

public record TaskLogResponse(
        Long id,
        Long tenantId,
        Long instanceId,
        Long taskId,
        String nodeName,
        Long operatorId,
        String operatorName,
        String action,
        String actionLabel,
        String comment,
        LocalDateTime createdAt
) {
}
