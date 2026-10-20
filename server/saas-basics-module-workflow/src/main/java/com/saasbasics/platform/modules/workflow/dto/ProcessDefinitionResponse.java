package com.saasbasics.platform.modules.workflow.dto;

import java.time.LocalDateTime;

public record ProcessDefinitionResponse(
        Long id,
        Long tenantId,
        String processCode,
        String processName,
        String category,
        String icon,
        String color,
        String description,
        String formSchemaJson,
        String flowNodesJson,
        Integer version,
        String status,
        Integer sortNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
