package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantBillEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantBillMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.outbox.OutboxEventHandler;
import com.saasbasics.platform.outbox.OutboxEventPayload;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商业化闭环消费者：监听 PAY_ORDER_SUCCEEDED Outbox 事件，原子性完成租户订阅续期/升级与已付账单凭证落库。
 */
@Component
public class PaySuccessOutboxHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaySuccessOutboxHandler.class);

    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<PlatTenantBillMapper> billMapperProvider;
    private final ObjectMapper objectMapper;

    public PaySuccessOutboxHandler(
            ObjectProvider<TenantMapper> tenantMapperProvider,
            ObjectProvider<PlatTenantBillMapper> billMapperProvider,
            ObjectMapper objectMapper) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.billMapperProvider = billMapperProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedEventType() {
        return "PAY_ORDER_SUCCEEDED";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(OutboxEventPayload event) throws Exception {
        JsonNode root = objectMapper.readTree(event.payloadJson());
        long tenantId = root.hasNonNull("tenantId") ? root.get("tenantId").asLong() : (event.tenantId() != null ? event.tenantId() : 1L);
        String orderNo = root.path("orderNo").asText(event.aggregateId());
        String payTradeNo = root.path("payTradeNo").asText(orderNo);
        String channel = root.path("channel").asText("ONLINE");
        String subject = root.path("subject").asText("SaaS 平台订阅付费");
        BigDecimal amount = root.hasNonNull("amount") ? new BigDecimal(root.get("amount").asText("0.00")) : BigDecimal.ZERO;
        int durationDays = root.hasNonNull("durationDays") ? Math.max(1, root.get("durationDays").asInt(30)) : 30;
        Long packageId = root.hasNonNull("packageId") ? root.get("packageId").asLong() : null;

        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        if (tenantMapper != null) {
            TenantEntity tenant = tenantMapper.selectById(tenantId);
            if (tenant != null) {
                LocalDateTime base = tenant.getExpireAt() != null && tenant.getExpireAt().isAfter(LocalDateTime.now())
                        ? tenant.getExpireAt()
                        : LocalDateTime.now();
                tenant.setExpireAt(base.plusDays(durationDays));
                if (packageId != null && packageId > 0) {
                    tenant.setPackageId(packageId);
                }
                if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus()) && !"ENABLED".equalsIgnoreCase(tenant.getStatus())) {
                    tenant.setStatus("ACTIVE");
                }
                tenantMapper.updateById(tenant);
                log.info("[Billing] 租户 {} 订阅续期生效 +{} 天 -> expireAt={}", tenantId, durationDays, tenant.getExpireAt());
            }
        }

        PlatTenantBillMapper billMapper = billMapperProvider.getIfAvailable();
        if (billMapper != null) {
            String billNo = "BIL_" + orderNo;
            PlatTenantBillEntity existing = billMapper.selectOne(new LambdaQueryWrapper<PlatTenantBillEntity>()
                    .eq(PlatTenantBillEntity::getBillNo, billNo)
                    .last("LIMIT 1"));
            if (existing == null) {
                PlatTenantBillEntity bill = new PlatTenantBillEntity();
                bill.setTenantId(tenantId);
                bill.setBillNo(billNo);
                bill.setBillingPeriod(YearMonth.now().toString());
                bill.setTotalAmount(amount);
                bill.setPaidAmount(amount);
                bill.setPaymentStatus("PAID");
                bill.setItemBreakdownJson(objectMapper.writeValueAsString(Map.of(
                        "orderNo", orderNo,
                        "payTradeNo", payTradeNo,
                        "channel", channel,
                        "subject", subject,
                        "durationDays", durationDays
                )));
                bill.setRemark("支付成交自动核销出账 (" + channel + ")");
                bill.setCreatedAt(LocalDateTime.now());
                bill.setUpdatedAt(LocalDateTime.now());
                billMapper.insert(bill);
            }
        }
    }
}
