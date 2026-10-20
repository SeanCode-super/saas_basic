package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ErpPurchaseOrderSaveRequest(
        @NotNull(message = "供应商不能为空")
        Long supplierId,
        String supplierName,
        BigDecimal totalAmount,
        String remark
) {}
