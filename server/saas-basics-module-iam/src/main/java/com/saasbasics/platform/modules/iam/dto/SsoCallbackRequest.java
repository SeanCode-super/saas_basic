package com.saasbasics.platform.modules.iam.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record SsoCallbackRequest(
        @NotBlank(message = "IDP类型不能为空")
        @JsonAlias({"channel"})
        String idpType,
        @NotBlank(message = "授权码Code不能为空")
        @JsonAlias({"authCode"})
        String code,
        String state,
        String tenantCode,
        @JsonAlias({"code_verifier"})
        String codeVerifier
) {
    public SsoCallbackRequest(String idpType, String code, String state) {
        this(idpType, code, state, "platform", null);
    }

    public SsoCallbackRequest(String idpType, String code, String state, String tenantCode) {
        this(idpType, code, state, tenantCode, null);
    }
}
