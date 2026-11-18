package com.saasbasics.platform.modules.integration.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.integration.entity.MessageDeliveryLogEntity;
import com.saasbasics.platform.modules.integration.mapper.MessageDeliveryLogMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 多渠道统一消息网关服务。
 * 集中管理短信 (阿里云/腾讯云/Mock)、邮件 (SMTP) 与群机器人的派发、持久化审计台账与通道探活。
 */
@Service
public class MessageGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MessageGatewayService.class);
    private static final int MAX_LOG_SIZE = 100;

    private final SmsClientFactory smsClientFactory;
    private final MailClient mailClient;
    private final EnterpriseBotClient enterpriseBotClient;
    private final SmsRateLimiter smsRateLimiter;
    private final ObjectProvider<MessageDeliveryLogMapper> deliveryLogMapperProvider;
    private final Deque<MessageLogItem> logQueue = new ConcurrentLinkedDeque<>();

    public MessageGatewayService(SmsClientFactory smsClientFactory, MailClient mailClient, EnterpriseBotClient enterpriseBotClient, SmsRateLimiter smsRateLimiter) {
        this(smsClientFactory, mailClient, enterpriseBotClient, smsRateLimiter, null);
    }

    @Autowired
    public MessageGatewayService(
            SmsClientFactory smsClientFactory,
            MailClient mailClient,
            EnterpriseBotClient enterpriseBotClient,
            SmsRateLimiter smsRateLimiter,
            ObjectProvider<MessageDeliveryLogMapper> deliveryLogMapperProvider
    ) {
        this.smsClientFactory = smsClientFactory;
        this.mailClient = mailClient;
        this.enterpriseBotClient = enterpriseBotClient;
        this.smsRateLimiter = smsRateLimiter;
        this.deliveryLogMapperProvider = deliveryLogMapperProvider;

        // 初始化内存兜底审计台账（仅当无持久化连接时生效）
        initSampleLogs();
    }

    public SmsSendResult sendSms(SmsSendRequest request) {
        // 工业级短信风控安全校验
        smsRateLimiter.checkRateLimit(request.phone());

        long start = System.currentTimeMillis();
        SmsClient client = smsClientFactory.getClient(request.channelCode());
        SmsSendResult result;
        try {
            result = client.sendSms(request);
            if (result.success()) {
                smsRateLimiter.recordSuccess(request.phone());
            }
        } catch (Exception e) {
            log.error("[MessageGateway] 短信发送异常: phone={}", request.phone(), e);
            result = SmsSendResult.failure(client.getChannelCode(), request.phone(), "SEND_ERROR", e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;

        recordLog(new MessageLogItem(
                UUID.randomUUID().toString(),
                "SMS",
                request.phone(),
                result.channelCode(),
                request.signName() != null ? request.signName() : "SaaS平台",
                "模板: " + (request.templateCode() != null ? request.templateCode() : "默认") + " | 参数: " + request.templateParams(),
                result.success() ? "SUCCESS" : "FAILED",
                result.messageId(),
                result.message(),
                cost,
                LocalDateTime.now()
        ));

        return result;
    }

    public MailSendResult sendMail(MailSendRequest request) {
        long start = System.currentTimeMillis();
        MailSendResult result;
        try {
            result = mailClient.sendMail(request);
        } catch (Exception e) {
            log.error("[MessageGateway] 邮件发送异常: to={}", request.to(), e);
            result = MailSendResult.failure(request.to(), request.subject(), e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;

        recordLog(new MessageLogItem(
                UUID.randomUUID().toString(),
                "MAIL",
                request.to(),
                "SMTP",
                request.subject(),
                request.content().length() > 60 ? request.content().substring(0, 60) + "..." : request.content(),
                result.success() ? "SUCCESS" : "FAILED",
                result.messageId(),
                result.message(),
                cost,
                LocalDateTime.now()
        ));

        return result;
    }

    public BotSendResult sendBot(BotSendRequest request) {
        BotSendResult result = enterpriseBotClient.send(request);
        recordLog(new MessageLogItem(
                UUID.randomUUID().toString(),
                "BOT",
                request.botType(),
                request.botType(),
                request.title(),
                request.content().length() > 60 ? request.content().substring(0, 60) + "..." : request.content(),
                result.success() ? "SUCCESS" : "FAILED",
                result.messageId(),
                result.message(),
                result.durationMs(),
                LocalDateTime.now()
        ));
        return result;
    }

    public Map<String, Object> getChannels() {
        Map<String, Object> map = new HashMap<>();
        map.put("smsChannels", smsClientFactory.listChannels());
        map.put("mailChannels", List.of(
                Map.of("channelCode", "SMTP", "channelName", "标准 SMTP 邮件网关", "status", "ACTIVE")
        ));
        map.put("botChannels", List.of(
                Map.of("channelCode", "WECHAT_WORK", "channelName", "企业微信群机器人 Webhook", "status", "ACTIVE"),
                Map.of("channelCode", "DINGTALK", "channelName", "钉钉自定义群机器人 Webhook", "status", "ACTIVE"),
                Map.of("channelCode", "FEISHU", "channelName", "飞书自定义群机器人 Webhook", "status", "ACTIVE")
        ));
        return map;
    }

    public List<MessageLogItem> getLogs() {
        MessageDeliveryLogMapper mapper = deliveryLogMapperProvider != null ? deliveryLogMapperProvider.getIfAvailable() : null;
        if (mapper != null) {
            try {
                List<MessageDeliveryLogEntity> rows = mapper.selectList(new LambdaQueryWrapper<MessageDeliveryLogEntity>()
                        .eq(MessageDeliveryLogEntity::getTenantId, currentTenantId())
                        .orderByDesc(MessageDeliveryLogEntity::getId)
                        .last("LIMIT 100"));
                if (!rows.isEmpty()) {
                    return rows.stream().map(e -> new MessageLogItem(
                            e.getMessageUid(),
                            e.getChannelType(),
                            e.getRecipient(),
                            e.getProviderCode(),
                            e.getSubject(),
                            e.getContentSummary(),
                            e.getStatus(),
                            e.getProviderMessageId(),
                            e.getErrorMessage(),
                            e.getCostMs() != null ? e.getCostMs() : 0L,
                            e.getCreatedAt()
                    )).toList();
                }
            } catch (Exception ex) {
                log.debug("[MessageGateway] 读取持久化台账回退内存日志: {}", ex.getMessage());
            }
        }
        return new ArrayList<>(logQueue);
    }

    public void clearLogs() {
        MessageDeliveryLogMapper mapper = deliveryLogMapperProvider != null ? deliveryLogMapperProvider.getIfAvailable() : null;
        if (mapper != null) {
            try {
                mapper.delete(new LambdaQueryWrapper<MessageDeliveryLogEntity>()
                        .eq(MessageDeliveryLogEntity::getTenantId, currentTenantId()));
            } catch (Exception ex) {
                log.debug("[MessageGateway] 清理持久化台账跳过: {}", ex.getMessage());
            }
        }
        logQueue.clear();
    }

    private void recordLog(MessageLogItem item) {
        MessageDeliveryLogMapper mapper = deliveryLogMapperProvider != null ? deliveryLogMapperProvider.getIfAvailable() : null;
        if (mapper != null) {
            try {
                MessageDeliveryLogEntity entity = new MessageDeliveryLogEntity();
                entity.setMessageUid(item.id());
                entity.setTenantId(currentTenantId());
                entity.setChannelType(item.type());
                entity.setProviderCode(item.channel() != null ? item.channel() : "DEFAULT");
                entity.setRecipient(item.target() != null ? item.target() : "");
                entity.setSubject(truncate(item.title(), 250));
                entity.setContentSummary(truncate(item.content(), 500));
                entity.setStatus(item.status());
                entity.setProviderMessageId(truncate(item.messageId(), 120));
                entity.setErrorMessage(truncate(item.responseMsg(), 500));
                entity.setCostMs(item.costMillis());
                entity.setCreatedAt(item.sentAt() != null ? item.sentAt() : LocalDateTime.now());
                mapper.insert(entity);
            } catch (Exception ex) {
                log.debug("[MessageGateway] 持久化消息审计失败，保留内存记录: {}", ex.getMessage());
            }
        }
        logQueue.addFirst(item);
        while (logQueue.size() > MAX_LOG_SIZE) {
            logQueue.pollLast();
        }
    }

    private static Long currentTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private static String truncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) {
            return val;
        }
        return val.substring(0, maxLen);
    }

    private void initSampleLogs() {
        logQueue.add(new MessageLogItem(
                UUID.randomUUID().toString(),
                "SMS",
                "13800138000",
                "MOCK",
                "【SaaS底座】",
                "您正在进行系统安全身份验证，验证码：849201，5分钟内有效。",
                "SUCCESS",
                "mock-init-01",
                "模拟发送成功",
                12,
                LocalDateTime.now().minusMinutes(5)
        ));
        logQueue.add(new MessageLogItem(
                UUID.randomUUID().toString(),
                "MAIL",
                "devops@saasbasics.com",
                "SMTP",
                "【系统监控警报】慢 SQL 拦截通知",
                "检测到执行超过 500ms 的慢 SQL 查询，请登录运维监控大屏查看分析详情。",
                "SUCCESS",
                "mail-init-01",
                "邮件已成功投递",
                45,
                LocalDateTime.now().minusMinutes(12)
        ));
    }
}
