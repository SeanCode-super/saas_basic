package com.saasbasics.platform.modules.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ErpPurchaseOrderResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierName,
        BigDecimal totalAmount,
        String status,
        String remark,
        LocalDateTime createdAt,
        Long warehouseId,
        String warehouseName,
        List<ErpPurchaseItemResponse> items
) {}
