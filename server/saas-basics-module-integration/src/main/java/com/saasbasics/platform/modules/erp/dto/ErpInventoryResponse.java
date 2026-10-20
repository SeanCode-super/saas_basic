package com.saasbasics.platform.modules.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ErpInventoryResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String specification,
        String unit,
        Long warehouseId,
        String warehouseName,
        Integer stockQuantity,
        Integer lockedQuantity,
        Integer availableQuantity,
        Integer minStock,
        Boolean isLowStock,
        BigDecimal totalValue,
        LocalDateTime updatedAt
) {}
