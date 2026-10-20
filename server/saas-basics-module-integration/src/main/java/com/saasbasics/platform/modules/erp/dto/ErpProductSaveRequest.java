package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ErpProductSaveRequest(
        @NotBlank(message = "物料编码不能为空")
        String productCode,
        @NotBlank(message = "物料名称不能为空")
        String productName,
        String category,
        String specification,
        String unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Integer minStock,
        String status,
        String remark
) {}
