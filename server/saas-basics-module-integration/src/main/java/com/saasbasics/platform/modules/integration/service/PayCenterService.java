package com.saasbasics.platform.modules.integration.service;

import com.saasbasics.platform.modules.integration.dto.PayOrderCreateRequest;
import com.saasbasics.platform.modules.integration.dto.PayOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级统一支付中心服务。
 * 屏蔽多渠道支付网关差异，提供统一统一下单、异步通知回调与对账能力。
 */
@Service
public class PayCenterService {

    private static final Logger log = LoggerFactory.getLogger(PayCenterService.class);

    // In-memory trade order registry for demonstration / mock sandbox
    private final Map<String, PayOrderResponse> tradeRegistry = new ConcurrentHashMap<>();

    public PayOrderResponse createPayOrder(Long tenantId, PayOrderCreateRequest request) {
        String tradeNo = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String channel = (request.channel() != null && !request.channel().isBlank())
                ? request.channel().toUpperCase()
                : "MOCK_SANDBOX";

        String payUrl;
        String qrCodeUrl;

        if ("WECHAT".equals(channel)) {
            payUrl = "weixin://wxpay/bizpayurl?pr=" + tradeNo;
            qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=" + payUrl;
        } else if ("ALIPAY".equals(channel)) {
            payUrl = "https://openapi.alipay.com/gateway.do?trade_no=" + tradeNo;
            qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=" + payUrl;
        } else {
            // MOCK_SANDBOX
            payUrl = "https://pay.saas-basics.local/sandbox?tradeNo=" + tradeNo;
            qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=" + payUrl;
        }

        String businessOrderNo = (request.orderNo() != null && !request.orderNo().isBlank())
                ? request.orderNo()
                : "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        PayOrderResponse order = new PayOrderResponse(
                tradeNo,
                businessOrderNo,
                request.amount(),
                channel,
                "PENDING",
                payUrl,
                qrCodeUrl,
                LocalDateTime.now().plusMinutes(30),
                null
        );

        tradeRegistry.put(businessOrderNo, order);
        tradeRegistry.put(tradeNo, order);

        log.info("Pay order created: tradeNo={}, orderNo={}, amount={}, channel={}",
                tradeNo, businessOrderNo, request.amount(), channel);

        return order;
    }

    public PayOrderResponse getOrderStatus(String orderNoOrTradeNo) {
        PayOrderResponse order = tradeRegistry.get(orderNoOrTradeNo);
        if (order != null) {
            return order;
        }
        return new PayOrderResponse(
                "UNKNOWN",
                orderNoOrTradeNo,
                null,
                "NONE",
                "NOT_FOUND",
                null,
                null,
                null,
                null
        );
    }

    public PayOrderResponse mockPaySuccess(String orderNoOrTradeNo) {
        PayOrderResponse current = tradeRegistry.get(orderNoOrTradeNo);
        if (current == null) {
            return null;
        }

        PayOrderResponse updated = new PayOrderResponse(
                current.payTradeNo(),
                current.orderNo(),
                current.amount(),
                current.channel(),
                "SUCCESS",
                current.payUrl(),
                current.qrCodeUrl(),
                current.expireAt(),
                LocalDateTime.now()
        );

        tradeRegistry.put(current.orderNo(), updated);
        tradeRegistry.put(current.payTradeNo(), updated);

        log.info("Mock payment success triggered for order: {}", orderNoOrTradeNo);
        return updated;
    }
}
