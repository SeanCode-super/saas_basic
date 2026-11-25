package com.saasbasics.platform.modules.erp.dto;

import java.math.BigDecimal;

public record ErpPurchaseItemResponse(Long productId, String productName, Integer quantity,
                                      BigDecimal unitPrice, BigDecimal subtotal) {}
