package com.saasbasics.platform.modules.integration.dto;

import java.time.LocalDateTime;

public record OpenAppResponse(
        Long id,
        Long tenantId,
        String appKey,
        String appSecret,
        String appName,
        String status,
        Integer rateLimit,
        String allowedPermissions,
        String remark,
        LocalDateTime createdAt
) {}
