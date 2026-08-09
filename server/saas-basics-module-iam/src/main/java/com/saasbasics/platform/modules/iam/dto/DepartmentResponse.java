package com.saasbasics.platform.modules.iam.dto;

public record DepartmentResponse(
        Long id,
        Long tenantId,
        Long parentId,
        String deptCode,
        String deptName,
        String deptFullName,
        String treePath,
        Integer treeLevel,
        Long leaderUserId,
        String leaderName,
        Long childCount,
        Long employeeCount,
        String status,
        Integer sortNo,
        String remark
) {
}
