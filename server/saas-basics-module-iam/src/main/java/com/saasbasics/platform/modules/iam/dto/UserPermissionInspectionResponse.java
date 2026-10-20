package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record UserPermissionInspectionResponse(
        Long userId,
        String username,
        String nickname,
        Long employeeId,
        String employeeName,
        Long departmentId,
        String departmentName,
        Long positionId,
        String positionName,
        Long companyDepartmentId,
        String companyDepartmentName,
        List<RoleSummary> roles,
        List<MenuSummary> menus,
        List<String> buttonPermissions,
        List<DataScopeSummary> dataScopeRules
) {
    public record RoleSummary(
            Long id,
            String roleCode,
            String roleName,
            String roleType,
            String status
    ) {}

    public record MenuSummary(
            Long id,
            Long parentId,
            String menuCode,
            String menuName,
            String menuType,
            String path,
            String icon
    ) {}

    public record DataScopeSummary(
            Long id,
            String resourceCode,
            String resourceName,
            String scopeType,
            String subjectType,
            String subjectValue,
            List<Long> resolvedDepartmentIds,
            String configSummary
    ) {}
}
