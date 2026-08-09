package com.saasbasics.platform.modules.codegen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CodegenProjectSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String basePackage,
        @NotBlank String modulePrefix,
        @NotBlank String outputMode,
        @NotBlank String status,
        String remark
) {
}
