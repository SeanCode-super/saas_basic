package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DepartmentSaveRequest(
        @NotNull Long tenantId,
        Long parentId,
        @NotBlank String deptCode,
        @NotBlank String deptName,
        String deptFullName,
        Long leaderUserId,
        @NotBlank String status,
        Integer sortNo,
        String remark
) {
}
