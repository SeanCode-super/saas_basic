package com.saasbasics.platform.modules.audit.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationLogDiffResponse(
        Long id,
        String bizModule,
        String bizType,
        String bizId,
        String operationType,
        String operatorName,
        LocalDateTime occurredAt,
        String beforeJson,
        String afterJson,
        List<FieldDiffItem> changedFields
) {
    public record FieldDiffItem(
            String fieldPath,
            String beforeValue,
            String afterValue,
            String changeType
    ) {
    }
}
