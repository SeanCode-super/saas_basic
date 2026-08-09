package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordPolicySaveRequest(
        @NotNull Long tenantId,
        @NotBlank String policyCode,
        @NotBlank String policyName,
        @NotNull Integer minLength,
        @NotNull Integer maxLength,
        @NotNull Boolean requireUppercase,
        @NotNull Boolean requireLowercase,
        @NotNull Boolean requireNumber,
        @NotNull Boolean requireSpecial,
        @NotNull Integer passwordHistoryLimit,
        @NotNull Integer passwordExpireDays,
        @NotNull Integer tempPasswordExpireHours,
        @NotBlank String status,
        String remark
) {
}
