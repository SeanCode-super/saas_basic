package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuSaveRequest(
        @NotNull Long tenantId,
        Long parentId,
        @NotBlank String menuType,
        @NotBlank String menuCode,
        @NotBlank String menuName,
        String routePath,
        String componentPath,
        String permissionCode,
        String icon,
        @NotNull Boolean visible,
        @NotNull Boolean keepAlive,
        Integer sortNo,
        @NotBlank String status,
        String metaJson,
        String remark
) {
}
