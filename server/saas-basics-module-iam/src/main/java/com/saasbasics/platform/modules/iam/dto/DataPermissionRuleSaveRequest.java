package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataPermissionRuleSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String resourceCode,
        @NotBlank String resourceName,
        @NotBlank String subjectType,
        @NotBlank String subjectValue,
        @NotBlank String scopeType,
        String configJson,
        @NotBlank String status,
        String remark
) {
}
