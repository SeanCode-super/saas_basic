package com.saasbasics.platform.modules.iam.dto;

public record RoleResponse(
        Long id,
        Long tenantId,
        Long roleGroupId,
        String roleCode,
        String roleName,
        String roleType,
        String dataScopeType,
        String status,
        Boolean system,
        Integer sortNo,
        String remark
) {
}
