package com.saasbasics.platform.modules.integration.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PayOrderCreateRequest(
        String orderNo,
        @NotNull(message = "支付金额不能为空")
        @DecimalMin(value = "0.01", message = "支付金额必须大于等于0.01元")
        BigDecimal amount,
        String channel, // WECHAT / ALIPAY / MOCK_SANDBOX
        String subject,
        String returnUrl
) {
}
