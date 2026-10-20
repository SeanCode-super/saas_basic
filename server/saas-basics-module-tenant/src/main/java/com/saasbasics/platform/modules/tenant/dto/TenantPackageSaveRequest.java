package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TenantPackageSaveRequest(
        @NotBlank String packageCode,
        @NotBlank String packageName,
        String packageType,
        String status,
        @NotNull Integer maxUserCount,
        @NotNull Integer maxDeptCount,
        @NotNull Integer maxStorageMb,
        @NotNull Integer maxApiPerMinute,
        String featureFlagsJson,
        Integer sortNo,
        String remark
) {
}
