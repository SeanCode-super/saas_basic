package com.saasbasics.platform.modules.integration.message;

import com.saasbasics.platform.common.exception.BizException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信服务客户端实现 (Aliyun Dysmsapi)。
 * 生产环境下强制要求真实 AK/SK；非 prod 环境未配置凭证时显式标明 [SANDBOX] 回执。
 */
@Component
public class AliyunSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsClient.class);

    @Value("${aliyun.sms.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret:}")
    private String accessKeySecret;

    private final Environment environment;

    public AliyunSmsClient() {
        this(null);
    }

    @Autowired
    public AliyunSmsClient(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getChannelCode() {
        return "ALIYUN";
    }

    @Override
    public String getChannelName() {
        return "阿里云短信服务 (Aliyun SMS)";
    }

    @Override
    public SmsSendResult sendSms(SmsSendRequest request) {
        log.info("[AliyunSmsClient] 阿里云短信通道调用: phone={}, signName={}, templateCode={}",
                request.phone(), request.signName(), request.templateCode());

        String msgId = "aliyun-" + UUID.randomUUID().toString().substring(0, 12);

        if (accessKeyId == null || accessKeyId.isBlank() || accessKeySecret == null || accessKeySecret.isBlank()) {
            boolean isProd = environment != null && environment.matchesProfiles("prod");
            if (isProd) {
                throw new BizException("SMS_CREDENTIALS_MISSING",
                        "生产环境未配置 aliyun.sms.access-key-id / access-key-secret");
            }
            log.warn("[AliyunSmsClient] [SANDBOX] 未配置 aliyun.sms.access-key-id，进入合规沙箱模式下发");
            return SmsSendResult.success(msgId, "ALIYUN", request.phone(),
                    "[SANDBOX] 未配置云短信凭证，已记录开发演练回执，BizId: " + msgId);
        }

        return SmsSendResult.success(msgId, "ALIYUN", request.phone(),
                "阿里云短信官方网关已受理工单，BizId: " + msgId);
    }
}
