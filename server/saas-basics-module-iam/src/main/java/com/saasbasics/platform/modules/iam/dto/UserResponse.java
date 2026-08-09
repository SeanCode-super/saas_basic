package com.saasbasics.platform.modules.iam.dto;

public record UserResponse(
        Long id,
        Long tenantId,
        String userCode,
        String username,
        String nickname,
        Long employeeId,
        String userType,
        String status,
        String mobile,
        String email,
        String remark
) {
}
