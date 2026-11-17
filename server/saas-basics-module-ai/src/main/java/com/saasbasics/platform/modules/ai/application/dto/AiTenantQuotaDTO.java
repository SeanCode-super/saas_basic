package com.saasbasics.platform.modules.ai.application.dto;

import java.math.BigDecimal;

public record AiTenantQuotaDTO(
        Long tenantId,
        Long monthlyTokenLimit,
        Long usedTokensMonth,
        Long promptTokensMonth,
        Long completionTokensMonth,
        Long remainingTokens,
        double usagePercent,
        BigDecimal estimatedCostMonth,
        String billingMonth,
        Integer rpmLimit,
        String status
) {
}
