package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PortalClientSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String clientId,
        @NotBlank String tenantCode,
        @NotBlank String clientName,
        @NotBlank String portalTitle,
        @NotBlank String welcomeTitle,
        @NotBlank String welcomeText,
        String logoUrl,
        @NotBlank String themeCode,
        String backgroundImageUrl,
        @NotBlank String backgroundColor,
        String filingInfo,
        @NotNull Long loginPolicyId,
        @NotNull Long passwordPolicyId,
        @NotBlank String captchaMode,
        @NotNull Boolean sliderReserved,
        @NotBlank String status,
        String remark
) {
}
