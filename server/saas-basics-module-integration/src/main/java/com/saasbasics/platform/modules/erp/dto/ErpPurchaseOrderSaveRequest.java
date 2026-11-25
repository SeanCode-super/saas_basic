package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ErpPurchaseOrderSaveRequest(
        @NotNull @Positive Long supplierId,
        @NotNull @Positive Long warehouseId,
        @Size(max = 500) String remark,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid ErpPurchaseItemRequest> items,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal totalAmount
) {}
