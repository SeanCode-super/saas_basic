package com.saasbasics.platform.modules.audit.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record OperationLogResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
