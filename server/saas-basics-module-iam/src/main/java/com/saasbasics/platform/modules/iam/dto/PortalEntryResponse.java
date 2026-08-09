package com.saasbasics.platform.modules.iam.dto;

public record PortalEntryResponse(
        String clientId,
        String clientName,
        String tenantCode,
        String portalTitle,
        String welcomeTitle,
        String welcomeText,
        String logoUrl,
        String themeCode,
        String backgroundImageUrl,
        String backgroundColor,
        String filingInfo,
        TerminalSnapshot terminal,
        LoginPolicySnapshot loginPolicy,
        PasswordPolicySnapshot passwordPolicy,
        CaptchaSnapshot captcha
) {

    public record TerminalSnapshot(
            String terminalCode,
            String terminalName,
            String terminalType
    ) {
    }

    public record LoginPolicySnapshot(
            Long id,
            String policyCode,
            String policyName,
            boolean allowPasswordLogin,
            boolean allowSmsLogin,
            boolean allowEmailLogin,
            boolean allowSocialLogin,
            boolean forceMfa,
            Integer sessionTimeoutMinutes,
            Integer maxFailedCount,
            Integer lockMinutes
    ) {
    }

    public record PasswordPolicySnapshot(
            Long id,
            String policyCode,
            String policyName,
            Integer minLength,
            Integer maxLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireNumber,
            boolean requireSpecial,
            Integer passwordHistoryLimit,
            Integer passwordExpireDays
    ) {
    }

    public record CaptchaSnapshot(
            String mode,
            boolean sliderReserved
    ) {
    }
}
