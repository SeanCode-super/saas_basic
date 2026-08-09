package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuPermissionSaveRequest(
        @NotNull Long tenantId,
        @NotNull Long menuId,
        @NotBlank String menuCode,
        @NotBlank String subjectType,
        @NotBlank String subjectValue,
        String buttonCodesJson,
        @NotBlank String status,
        String remark
) {
}
