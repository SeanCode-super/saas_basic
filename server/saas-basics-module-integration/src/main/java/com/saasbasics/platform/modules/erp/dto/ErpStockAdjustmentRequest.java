package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.constraints.*;

public record ErpStockAdjustmentRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long warehouseId,
        @NotBlank @Pattern(regexp = "IN|OUT|AUDIT") String type,
        @NotNull @PositiveOrZero Integer delta,
        @Size(max = 256) String remark
) {}
