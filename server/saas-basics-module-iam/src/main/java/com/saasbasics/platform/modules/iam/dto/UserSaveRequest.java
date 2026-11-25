package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record UserSaveRequest(
        Long tenantId,
        @NotBlank String userCode,
        @NotBlank String username,
        @NotBlank String nickname,
        Long employeeId,
        @NotBlank String userType,
        @NotBlank String status,
        String mobile,
        String email,
        String password,
        Boolean needResetPassword,
        String remark
) {
}
