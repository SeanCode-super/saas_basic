package com.saasbasics.platform.modules.tenant.dto;

public record TenantPackageResponse(
        Long id,
        String packageCode,
        String packageName,
        String packageType,
        String status,
        Integer maxUserCount,
        Integer maxDeptCount,
        Integer maxStorageMb,
        Integer maxApiPerMinute,
        String featureFlagsJson,
        Integer sortNo,
        String remark,
        Long activeTenantCount
) {
}
