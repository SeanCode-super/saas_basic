package com.saasbasics.platform.modules.iam.dto;

public record LoginPolicyResponse(
        Long id,
        Long tenantId,
        String policyCode,
        String policyName,
        Boolean allowPasswordLogin,
        Boolean allowSmsLogin,
        Boolean allowEmailLogin,
        Boolean allowSocialLogin,
        Boolean forceMfa,
        Integer sessionTimeoutMinutes,
        Integer maxFailedCount,
        Integer lockMinutes,
        String ipAllowlistJson,
        Integer deviceTrustDays,
        String status,
        String remark
) {
}
