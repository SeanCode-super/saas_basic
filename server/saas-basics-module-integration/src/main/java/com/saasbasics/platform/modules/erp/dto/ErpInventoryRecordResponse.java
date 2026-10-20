package com.saasbasics.platform.modules.erp.dto;

import java.time.LocalDateTime;

public record ErpInventoryRecordResponse(
        Long id,
        Long tenantId,
        String recordNo,
        String recordType,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        Integer changeQuantity,
        Integer beforeQuantity,
        Integer afterQuantity,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {}
