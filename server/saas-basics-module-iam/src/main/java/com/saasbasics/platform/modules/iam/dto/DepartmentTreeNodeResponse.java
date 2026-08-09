package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record DepartmentTreeNodeResponse(
        Long id,
        Long parentId,
        String deptCode,
        String deptName,
        String deptFullName,
        Integer treeLevel,
        String leaderName,
        Long childCount,
        Long employeeCount,
        String status,
        List<DepartmentTreeNodeResponse> children
) {
}
