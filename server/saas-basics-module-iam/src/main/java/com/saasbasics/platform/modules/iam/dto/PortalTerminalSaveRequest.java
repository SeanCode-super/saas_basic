package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PortalTerminalSaveRequest(
        @NotNull Long tenantId,
        @NotNull Long portalClientId,
        @NotBlank String terminalCode,
        @NotBlank String terminalName,
        @NotBlank String terminalType,
        @NotBlank String portalTitle,
        String logoUrl,
        @NotBlank String themeCode,
        String backgroundImageUrl,
        @NotBlank String backgroundColor,
        @NotNull Long loginPolicyId,
        @NotNull Long passwordPolicyId,
        @NotBlank String captchaMode,
        @NotNull Boolean sliderReserved,
        @NotNull Boolean isDefault,
        @NotBlank String status,
        String remark
) {
}
