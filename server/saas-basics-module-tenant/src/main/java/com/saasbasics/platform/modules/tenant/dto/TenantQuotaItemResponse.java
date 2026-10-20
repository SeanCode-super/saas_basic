package com.saasbasics.platform.modules.tenant.dto;

public record TenantQuotaItemResponse(
        String quotaCode,
        String quotaName,
        Long quotaValue,
        Long usedValue,
        Double usagePercent,
        String status
) {
}
