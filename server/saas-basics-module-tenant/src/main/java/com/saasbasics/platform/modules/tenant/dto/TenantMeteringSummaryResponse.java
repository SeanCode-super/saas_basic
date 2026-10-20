package com.saasbasics.platform.modules.tenant.dto;

public record TenantMeteringSummaryResponse(
        Long tenantId,
        Long totalApiCalls,
        Long apiCallsToday,
        Long storageUsedBytes,
        Long storageQuotaBytes,
        Integer activeSeats,
        Integer seatQuota,
        Double estimatedCostThisMonth
) {}
