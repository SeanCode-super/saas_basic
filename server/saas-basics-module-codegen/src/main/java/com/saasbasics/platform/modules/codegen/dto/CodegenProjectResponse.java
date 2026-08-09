package com.saasbasics.platform.modules.codegen.dto;

public record CodegenProjectResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String basePackage,
        String modulePrefix,
        String outputMode,
        String status,
        String remark
) {
}
