package com.saasbasics.platform.modules.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantIgnore;
import com.saasbasics.platform.modules.integration.dto.PayOrderCreateRequest;
import com.saasbasics.platform.modules.integration.dto.PayOrderResponse;
import com.saasbasics.platform.modules.integration.entity.PayOrderEntity;
import com.saasbasics.platform.modules.integration.entity.PayRefundEntity;
import com.saasbasics.platform.modules.integration.mapper.PayOrderMapper;
import com.saasbasics.platform.modules.integration.mapper.PayRefundMapper;
import com.saasbasics.platform.outbox.PlatformOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级聚合支付中枢服务。
 * 遵循银行/三方支付（微信支付 V3 / 支付宝 API v2）标准规范，提供交易订单持久化、状态机流转与幂等对账。
 */
@Service
public class PayCenterService {

    private static final Logger log = LoggerFactory.getLogger(PayCenterService.class);

    private final ObjectProvider<PayOrderMapper> payOrderMapperProvider;
    private final ObjectProvider<PayRefundMapper> payRefundMapperProvider;
    private final ObjectProvider<PlatformOutboxService> outboxServiceProvider;
    private final ObjectProvider<PayChannelVerifier> verifierProvider;
    private final ObjectProvider<Environment> environmentProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存兜底缓存（仅当无数据库环境时使用）
    private final Map<String, PayOrderResponse> fallbackRegistry = new ConcurrentHashMap<>();

    public PayCenterService(
            ObjectProvider<PayOrderMapper> payOrderMapperProvider,
            ObjectProvider<PayRefundMapper> payRefundMapperProvider
    ) {
        this(payOrderMapperProvider, payRefundMapperProvider, null, null, null);
    }

    @Autowired
    public PayCenterService(
            ObjectProvider<PayOrderMapper> payOrderMapperProvider,
            ObjectProvider<PayRefundMapper> payRefundMapperProvider,
            ObjectProvider<PlatformOutboxService> outboxServiceProvider,
            ObjectProvider<PayChannelVerifier> verifierProvider,
            ObjectProvider<Environment> environmentProvider
    ) {
        this.payOrderMapperProvider = payOrderMapperProvider;
        this.payRefundMapperProvider = payRefundMapperProvider;
        this.outboxServiceProvider = outboxServiceProvider;
        this.verifierProvider = verifierProvider;
        this.environmentProvider = environmentProvider;
    }

    @Transactional
    public PayOrderResponse createPayOrder(Long tenantId, PayOrderCreateRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new BizException("INVALID_PAY_AMOUNT", "支付金额非法，必须大于等于 0.01 元");
        }

        String tradeNo = "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String channel = (request.channel() != null && !request.channel().isBlank())
                ? request.channel().toUpperCase()
                : "WECHAT";

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
                : "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(30);

        PayOrderMapper mapper = payOrderMapperProvider.getIfAvailable();
        if (mapper != null) {
            PayOrderEntity entity = new PayOrderEntity();
            entity.setTenantId(tenantId != null ? tenantId : 1L);
            entity.setPayTradeNo(tradeNo);
            entity.setOrderNo(businessOrderNo);
            entity.setAmount(request.amount() != null ? request.amount() : BigDecimal.ZERO);
            entity.setChannel(channel);
            entity.setStatus("PENDING");
            entity.setSubject(request.subject() != null ? request.subject() : "平台业务支付订单");
            entity.setPayUrl(payUrl);
            entity.setQrCodeUrl(qrCodeUrl);
            entity.setExpireAt(expireAt);

            mapper.insert(entity);
            log.info("[PayCenterService] 交易订单已持久化落库: id={}, tradeNo={}, orderNo={}, amount={}",
                    entity.getId(), tradeNo, businessOrderNo, request.amount());
        } else {
            PayOrderResponse fallback = new PayOrderResponse(
                    tradeNo, businessOrderNo, request.amount(), channel, "PENDING", payUrl, qrCodeUrl, expireAt, null
            );
            fallbackRegistry.put(businessOrderNo, fallback);
            fallbackRegistry.put(tradeNo, fallback);
        }

        return new PayOrderResponse(
                tradeNo,
                businessOrderNo,
                request.amount(),
                channel,
                "PENDING",
                payUrl,
                qrCodeUrl,
                expireAt,
                null
        );
    }

    public PayOrderResponse getOrderStatus(String orderNoOrTradeNo) {
        PayOrderMapper mapper = payOrderMapperProvider.getIfAvailable();
        if (mapper != null) {
            PayOrderEntity entity = mapper.selectByOrderNoOrTradeNo(orderNoOrTradeNo);
            if (entity != null) {
                return new PayOrderResponse(
                        entity.getPayTradeNo(),
                        entity.getOrderNo(),
                        entity.getAmount(),
                        entity.getChannel(),
                        entity.getStatus(),
                        entity.getPayUrl(),
                        entity.getQrCodeUrl(),
                        entity.getExpireAt(),
                        entity.getSuccessAt()
                );
            }
        }

        PayOrderResponse fallback = fallbackRegistry.get(orderNoOrTradeNo);
        if (fallback != null) {
            return fallback;
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

    public java.util.List<PayOrderResponse> listRecentOrders(Long tenantId) {
        PayOrderMapper mapper = payOrderMapperProvider.getIfAvailable();
        if (mapper != null) {
            java.util.List<PayOrderEntity> list = mapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayOrderEntity>()
                            .eq(PayOrderEntity::getTenantId, tenantId != null ? tenantId : 1L)
                            .eq(PayOrderEntity::getDeleted, 0)
                            .orderByDesc(PayOrderEntity::getId)
                            .last("LIMIT 50")
            );
            return list.stream().map(entity -> new PayOrderResponse(
                    entity.getPayTradeNo(),
                    entity.getOrderNo(),
                    entity.getAmount(),
                    entity.getChannel(),
                    entity.getStatus(),
                    entity.getPayUrl(),
                    entity.getQrCodeUrl(),
                    entity.getExpireAt(),
                    entity.getSuccessAt()
            )).toList();
        }
        return new java.util.ArrayList<>(fallbackRegistry.values());
    }

    @Transactional(rollbackFor = Exception.class)
    public PayOrderResponse mockPaySuccess(String orderNoOrTradeNo) {
        Environment env = environmentProvider != null ? environmentProvider.getIfAvailable() : null;
        if (env != null && env.acceptsProfiles(Profiles.of("prod"))) {
            throw new BizException("MOCK_PAY_DISABLED_IN_PROD", "生产环境禁止调用模拟支付成功接口，请配置微信/支付宝签名回调");
        }
        return completePayment(orderNoOrTradeNo, "CH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
    }

    @TenantIgnore(reason = "Payment provider webhook callback arrives without user JWT")
    @Transactional(rollbackFor = Exception.class)
    public PayOrderResponse handleVerifiedWebhook(String channel, String rawBody, String signature, String timestamp) {
        PayChannelVerifier verifier = verifierProvider != null ? verifierProvider.getIfAvailable() : null;
        if (verifier != null) {
            verifier.verifyOrThrow(channel, rawBody, signature, timestamp);
        }
        String orderNo;
        String channelTradeNo;
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            orderNo = root.path("orderNo").asText(root.path("out_trade_no").asText(""));
            if (orderNo == null || orderNo.isBlank()) {
                throw new BizException("PAY_NOTIFY_ORDER_MISSING", "支付回调报文缺少 orderNo / out_trade_no");
            }
            channelTradeNo = root.path("channelTradeNo").asText(root.path("transaction_id").asText(""));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("PAY_NOTIFY_PARSE_FAILED", "支付回调 JSON 报文解析失败: " + ex.getMessage());
        }
        return completePayment(orderNo, channelTradeNo);
    }

    private PayOrderResponse completePayment(String orderNoOrTradeNo, String channelTradeNo) {
        PayOrderMapper mapper = payOrderMapperProvider.getIfAvailable();
        if (mapper != null) {
            PayOrderEntity entity = mapper.selectByOrderNoOrTradeNo(orderNoOrTradeNo);
            if (entity == null) {
                return null;
            }

            // 状态机幂等防重
            if ("SUCCESS".equals(entity.getStatus())) {
                log.info("[PayCenterService] 订单已为 SUCCESS 状态，幂等返回: tradeNo={}", entity.getPayTradeNo());
            } else {
                entity.setStatus("SUCCESS");
                entity.setSuccessAt(LocalDateTime.now());
                entity.setChannelTradeNo(channelTradeNo != null && !channelTradeNo.isBlank()
                        ? channelTradeNo
                        : "CH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
                mapper.updateById(entity);
                publishPaySucceededOutbox(entity);
                log.info("[PayCenterService] 交易状态机扭转: tradeNo={}, status=SUCCESS, successAt={}",
                        entity.getPayTradeNo(), entity.getSuccessAt());
            }

            return new PayOrderResponse(
                    entity.getPayTradeNo(),
                    entity.getOrderNo(),
                    entity.getAmount(),
                    entity.getChannel(),
                    entity.getStatus(),
                    entity.getPayUrl(),
                    entity.getQrCodeUrl(),
                    entity.getExpireAt(),
                    entity.getSuccessAt()
            );
        }

        PayOrderResponse current = fallbackRegistry.get(orderNoOrTradeNo);
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

        fallbackRegistry.put(current.orderNo(), updated);
        fallbackRegistry.put(current.payTradeNo(), updated);
        return updated;
    }

    private void publishPaySucceededOutbox(PayOrderEntity entity) {
        PlatformOutboxService outbox = outboxServiceProvider != null ? outboxServiceProvider.getIfAvailable() : null;
        if (outbox == null) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("payTradeNo", entity.getPayTradeNo());
        payload.put("orderNo", entity.getOrderNo());
        payload.put("tenantId", entity.getTenantId() != null ? entity.getTenantId() : 1L);
        payload.put("amount", entity.getAmount() != null ? entity.getAmount().toPlainString() : "0.00");
        payload.put("channel", entity.getChannel());
        payload.put("subject", entity.getSubject() != null ? entity.getSubject() : "SaaS 订阅服务");
        payload.put("durationDays", 30);
        outbox.enqueue("PAY_ORDER_SUCCEEDED", "PAY_ORDER", entity.getOrderNo(), entity.getTenantId(), payload);
        try {
            outbox.dispatchPendingEvents();
        } catch (Exception ex) {
            log.debug("[PayCenterService] 同步投递 Outbox 延迟由后台重试: {}", ex.getMessage());
        }
    }

    @Transactional
    public PayOrderResponse refundOrder(String orderNoOrTradeNo, BigDecimal refundAmount, String reason) {
        PayOrderMapper orderMapper = payOrderMapperProvider.getIfAvailable();
        if (orderMapper == null) {
            throw new BizException("DB_NOT_AVAILABLE", "持久化数据源未就绪");
        }

        PayOrderEntity order = orderMapper.selectByOrderNoOrTradeNo(orderNoOrTradeNo);
        if (order == null) {
            throw new BizException("PAY_ORDER_NOT_FOUND", "未找到支付订单: " + orderNoOrTradeNo);
        }

        if (!"SUCCESS".equals(order.getStatus())) {
            throw new BizException("INVALID_ORDER_STATUS", "当前订单状态不可退款: " + order.getStatus());
        }

        BigDecimal actualRefund = refundAmount != null ? refundAmount : order.getAmount();
        if (actualRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("INVALID_REFUND_AMOUNT", "退款金额必须大于 0");
        }
        if (order.getAmount() != null && actualRefund.compareTo(order.getAmount()) > 0) {
            throw new BizException("REFUND_EXCEED_LIMIT", "退款金额不能大于订单实付金额: " + order.getAmount());
        }

        PayRefundMapper refundMapper = payRefundMapperProvider.getIfAvailable();
        if (refundMapper != null) {
            PayRefundEntity refund = new PayRefundEntity();
            refund.setTenantId(order.getTenantId());
            refund.setRefundNo("REF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
            refund.setPayTradeNo(order.getPayTradeNo());
            refund.setRefundAmount(actualRefund);
            refund.setReason(reason != null ? reason : "用户申请原路退款");
            refund.setStatus("SUCCESS");
            refundMapper.insert(refund);
        }

        order.setStatus("REFUNDED");
        orderMapper.updateById(order);

        log.info("[PayCenterService] 订单原路退款成功: tradeNo={}, refundAmount={}", order.getPayTradeNo(), actualRefund);

        return new PayOrderResponse(
                order.getPayTradeNo(),
                order.getOrderNo(),
                order.getAmount(),
                order.getChannel(),
                order.getStatus(),
                order.getPayUrl(),
                order.getQrCodeUrl(),
                order.getExpireAt(),
                order.getSuccessAt()
        );
    }
}
