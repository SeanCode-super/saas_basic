package com.saasbasics.platform.modules.codegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record DynamicModelPublishRequest(
        @NotBlank(message = "modelCode is required")
        String modelCode,
        @NotBlank(message = "modelName is required")
        String modelName,
        String category,
        String icon,
        String description,
        @NotEmpty(message = "fields cannot be empty")
        List<DynamicFieldDto> fields,
        Boolean seedSampleData,
        List<Map<String, Object>> sampleData
) {
}
