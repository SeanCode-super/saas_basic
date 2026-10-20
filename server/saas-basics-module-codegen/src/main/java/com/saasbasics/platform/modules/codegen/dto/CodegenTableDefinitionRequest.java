package com.saasbasics.platform.modules.codegen.dto;

import java.util.List;

public record CodegenTableDefinitionRequest(
        String tableName,
        String tableComment,
        String className,
        String moduleName,
        String businessName,
        String functionName,
        String basePackage,
        List<CodegenColumnDefinition> columns,
        String templateType,
        String subTableName,
        String subTableComment,
        String subClassName,
        String subTableFk,
        List<CodegenColumnDefinition> subColumns,
        String treeId,
        String treeParentId,
        String treeName
) {
    public record CodegenColumnDefinition(
            String columnName,
            String fieldName,
            String columnType,
            String javaType,
            String comment,
            Boolean isPk,
            Boolean isRequired,
            Boolean isQuery,
            String queryType,
            String componentType,
            String dictCode
    ) {}
}
