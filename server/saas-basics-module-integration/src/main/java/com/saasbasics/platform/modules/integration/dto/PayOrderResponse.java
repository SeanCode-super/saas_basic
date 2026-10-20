package com.saasbasics.platform.modules.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayOrderResponse(
        String payTradeNo,
        String orderNo,
        BigDecimal amount,
        String channel,
        String status, // PENDING / SUCCESS / FAILED / REFUNDED
        String payUrl,
        String qrCodeUrl,
        LocalDateTime expireAt,
        LocalDateTime successAt
) {
}
