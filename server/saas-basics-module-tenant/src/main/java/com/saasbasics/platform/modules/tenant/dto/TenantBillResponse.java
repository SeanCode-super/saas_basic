package com.saasbasics.platform.modules.tenant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TenantBillResponse(
        Long id,
        Long tenantId,
        String billNo,
        String billingPeriod,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        String paymentStatus,
        String itemBreakdownJson,
        String remark,
        LocalDateTime createdAt
) {}
