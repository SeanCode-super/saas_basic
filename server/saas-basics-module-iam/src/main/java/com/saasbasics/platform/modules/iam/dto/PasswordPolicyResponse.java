package com.saasbasics.platform.modules.iam.dto;

public record PasswordPolicyResponse(
        Long id,
        Long tenantId,
        String policyCode,
        String policyName,
        Integer minLength,
        Integer maxLength,
        Boolean requireUppercase,
        Boolean requireLowercase,
        Boolean requireNumber,
        Boolean requireSpecial,
        Integer passwordHistoryLimit,
        Integer passwordExpireDays,
        Integer tempPasswordExpireHours,
        String status,
        String remark
) {
}
