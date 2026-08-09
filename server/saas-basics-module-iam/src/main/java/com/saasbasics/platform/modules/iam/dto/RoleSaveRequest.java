package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleSaveRequest(
        @NotNull Long tenantId,
        Long roleGroupId,
        @NotBlank String roleCode,
        @NotBlank String roleName,
        @NotBlank String roleType,
        @NotBlank String dataScopeType,
        @NotBlank String status,
        Boolean system,
        Integer sortNo,
        String remark
) {
}
