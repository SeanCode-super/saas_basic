package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ErpPurchaseItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice
) {}
