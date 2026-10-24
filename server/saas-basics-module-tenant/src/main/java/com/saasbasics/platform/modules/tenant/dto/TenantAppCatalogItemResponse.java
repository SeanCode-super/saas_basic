package com.saasbasics.platform.modules.tenant.dto;

import java.time.LocalDateTime;

public record TenantAppCatalogItemResponse(
        String appCode,
        String appName,
        String description,
        String category,
        String icon,
        String requiredTier,
        String status, // ENABLED, DISABLED, TRIAL, EXPIRED
        LocalDateTime openedAt,
        LocalDateTime expiredAt,
        Long daysRemaining,
        Boolean isTrial,
        Boolean isIncludedInPlan,
        Boolean canActivate
) {
}
