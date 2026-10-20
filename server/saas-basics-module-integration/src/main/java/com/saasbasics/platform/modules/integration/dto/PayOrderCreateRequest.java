package com.saasbasics.platform.modules.integration.dto;

import java.math.BigDecimal;

public record PayOrderCreateRequest(
        String orderNo,
        BigDecimal amount,
        String channel, // WECHAT / ALIPAY / MOCK_SANDBOX
        String subject,
        String returnUrl
) {
}
