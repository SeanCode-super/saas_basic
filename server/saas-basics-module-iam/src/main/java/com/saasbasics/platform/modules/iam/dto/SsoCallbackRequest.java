package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoCallbackRequest(
        @NotBlank(message = "IDP类型不能为空")
        String idpType,
        @NotBlank(message = "授权码Code不能为空")
        String code,
        String state,
        String tenantCode
) {
    public SsoCallbackRequest(String idpType, String code, String state) {
        this(idpType, code, state, "platform");
    }
}
