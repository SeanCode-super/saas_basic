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
        String activeMenu,
        String redirect,
        String componentPath,
        String componentName,
        String permissionCode,
        String icon,
        @NotNull Boolean visible,
        @NotNull Boolean keepAlive,
        Boolean isFrame,
        String frameSrc,
        Boolean isAffix,
        Integer sortNo,
        @NotBlank String status,
        String metaJson,
        String remark
) {
    public MenuSaveRequest(
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
        this(tenantId, parentId, menuType, menuCode, menuName, routePath, null, null,
             componentPath, null, permissionCode, icon, visible, keepAlive, false, null, false,
             sortNo, status, metaJson, remark);
    }
}
