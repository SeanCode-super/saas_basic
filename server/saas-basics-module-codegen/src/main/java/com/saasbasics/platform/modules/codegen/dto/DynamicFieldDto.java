package com.saasbasics.platform.modules.codegen.dto;

public record DynamicFieldDto(
        Long id,
        String fieldCode,
        String fieldName,
        String columnType,
        String javaType,
        String componentType,
        Boolean isRequired,
        Boolean isQuery,
        String queryType,
        String dictCode,
        String optionsJson,
        Integer sortNo
) {
}
