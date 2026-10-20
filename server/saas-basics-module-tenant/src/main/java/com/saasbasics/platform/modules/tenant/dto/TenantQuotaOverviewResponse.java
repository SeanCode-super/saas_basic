package com.saasbasics.platform.modules.tenant.dto;

import java.util.List;

public record TenantQuotaOverviewResponse(
        Long tenantId,
        String tenantCode,
        String tenantName,
        Long packageId,
        String packageName,
        String expireAt,
        Long daysRemaining,
        String lifecycleStatus,
        List<TenantQuotaItemResponse> quotas
) {
}
