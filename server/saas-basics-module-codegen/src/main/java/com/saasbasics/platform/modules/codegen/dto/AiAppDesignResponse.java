package com.saasbasics.platform.modules.codegen.dto;

import java.util.List;
import java.util.Map;

public record AiAppDesignResponse(
        String modelCode,
        String modelName,
        String tableName,
        String category,
        String icon,
        String description,
        List<DynamicFieldDto> fields,
        List<Map<String, Object>> sampleData
) {
}
