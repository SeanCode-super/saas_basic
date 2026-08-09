package com.saasbasics.platform.modules.iam.dto;

public record MenuResponse(
        Long id,
        Long tenantId,
        Long parentId,
        String menuType,
        String menuCode,
        String menuName,
        String routePath,
        String componentPath,
        String permissionCode,
        String icon,
        Boolean visible,
        Boolean keepAlive,
        Integer sortNo,
        String status,
        String metaJson,
        String remark
) {
}
