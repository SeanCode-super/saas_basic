package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record SsoConfigSaveRequest(
        Long id,
        @NotBlank(message = "身份源类型不能为空 (WECOM/FEISHU/DINGTALK/SAML/OIDC)")
        String idpType,
        @NotBlank(message = "AppId/ClientId 不能为空")
        String appId,
        String appSecret,
        String redirectUri,
        String status,
        Integer autoCreateUser,
        String configJson,
        String remark
) {}
