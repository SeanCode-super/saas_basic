package com.saasbasics.platform.modules.iam.dto;

public record PortalTerminalResponse(
        Long id,
        Long tenantId,
        Long portalClientId,
        String terminalCode,
        String terminalName,
        String terminalType,
        String portalTitle,
        String logoUrl,
        String themeCode,
        String backgroundImageUrl,
        String backgroundColor,
        Long loginPolicyId,
        Long passwordPolicyId,
        String captchaMode,
        Boolean sliderReserved,
        Boolean isDefault,
        String status,
        String remark
) {
}
