package com.saasbasics.platform.modules.codegen.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DynamicModelResponse(
        Long id,
        Long tenantId,
        String modelCode,
        String modelName,
        String tableName,
        String category,
        String icon,
        String description,
        String status,
        Long menuId,
        String configJson,
        List<DynamicFieldDto> fields,
        LocalDateTime createdAt
) {
}
