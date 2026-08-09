package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataScopeSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String scopeCode,
        @NotBlank String scopeName,
        @NotBlank String scopeType,
        String scopeRuleJson,
        @NotBlank String status,
        String remark
) {
}
