package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;

public record SsoConfigResponse(
        Long id,
        Long tenantId,
        String idpType,
        String appId,
        String redirectUri,
        String status,
        Integer autoCreateUser,
        String configJson,
        String remark,
        LocalDateTime createdAt
) {}
