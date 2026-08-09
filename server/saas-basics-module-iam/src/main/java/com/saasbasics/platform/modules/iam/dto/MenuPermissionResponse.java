package com.saasbasics.platform.modules.iam.dto;

public record MenuPermissionResponse(
        Long id,
        Long tenantId,
        Long menuId,
        String menuCode,
        String subjectType,
        String subjectValue,
        String buttonCodesJson,
        String status,
        String remark
) {
}
