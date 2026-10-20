package com.saasbasics.platform.modules.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ErpProductResponse(
        Long id,
        String productCode,
        String productName,
        String category,
        String specification,
        String unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Integer minStock,
        String status,
        String remark,
        LocalDateTime createdAt
) {}
