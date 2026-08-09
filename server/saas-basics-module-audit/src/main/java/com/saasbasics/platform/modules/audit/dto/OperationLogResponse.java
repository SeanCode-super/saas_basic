package com.saasbasics.platform.modules.audit.dto;

import java.time.LocalDateTime;

public record OperationLogResponse(
        Long id,
        Long tenantId,
        Long operatorUserId,
        String operatorName,
        String bizModule,
        String bizType,
        String bizId,
        String operationType,
        Boolean success,
        LocalDateTime occurredAt
) {
}
