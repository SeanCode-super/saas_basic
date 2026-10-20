package com.saasbasics.platform.modules.integration.dto;

public record OpenAppAuthContext(
        Long id,
        Long tenantId,
        String appKey,
        String appName,
        String status,
        Integer rateLimit,
        String allowedPermissions
) {}
