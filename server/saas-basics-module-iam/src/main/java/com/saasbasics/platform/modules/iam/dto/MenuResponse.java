package com.saasbasics.platform.modules.iam.dto;

public record MenuResponse(
        Long id,
        Long tenantId,
        Long parentId,
        String menuType,
        String menuCode,
        String menuName,
        String routePath,
        String activeMenu,
        String redirect,
        String componentPath,
        String componentName,
        String permissionCode,
        String icon,
        Boolean visible,
        Boolean keepAlive,
        Boolean isFrame,
        String frameSrc,
        Boolean isAffix,
        Integer sortNo,
        String status,
        String metaJson,
        String remark
) {
    public MenuResponse(
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
        this(id, tenantId, parentId, menuType, menuCode, menuName, routePath, null, null,
             componentPath, null, permissionCode, icon, visible, keepAlive, false, null, false,
             sortNo, status, metaJson, remark);
    }
}
