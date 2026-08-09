package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginPolicySaveRequest(
        @NotNull Long tenantId,
        @NotBlank String policyCode,
        @NotBlank String policyName,
        @NotNull Boolean allowPasswordLogin,
        @NotNull Boolean allowSmsLogin,
        @NotNull Boolean allowEmailLogin,
        @NotNull Boolean allowSocialLogin,
        @NotNull Boolean forceMfa,
        @NotNull Integer sessionTimeoutMinutes,
        @NotNull Integer maxFailedCount,
        @NotNull Integer lockMinutes,
        String ipAllowlistJson,
        @NotNull Integer deviceTrustDays,
        @NotBlank String status,
        String remark
) {
}
