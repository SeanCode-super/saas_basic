package com.saasbasics.platform.modules.iam.dto;

public record PortalClientResponse(
        Long id,
        Long tenantId,
        String clientId,
        String tenantCode,
        String clientName,
        String portalTitle,
        String welcomeTitle,
        String welcomeText,
        String logoUrl,
        String themeCode,
        String backgroundImageUrl,
        String backgroundColor,
        String filingInfo,
        Long loginPolicyId,
        Long passwordPolicyId,
        String captchaMode,
        Boolean sliderReserved,
        String status,
        String remark
) {
}
