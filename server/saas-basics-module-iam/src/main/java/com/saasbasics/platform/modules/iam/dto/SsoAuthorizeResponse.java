package com.saasbasics.platform.modules.iam.dto;

public record SsoAuthorizeResponse(
        String idpType,
        String authorizeUrl,
        String state
) {}
