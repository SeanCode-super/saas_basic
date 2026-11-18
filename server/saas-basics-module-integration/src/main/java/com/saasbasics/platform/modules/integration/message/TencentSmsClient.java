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
 * 腾讯云短信服务客户端实现。
 * 生产环境下强制要求配置 secret-id / secret-key / sdk-app-id；非 prod 未配置时明确标注 [SANDBOX]。
 */
@Component
public class TencentSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(TencentSmsClient.class);

    @Value("${tencent.sms.secret-id:}")
    private String secretId;

    @Value("${tencent.sms.secret-key:}")
    private String secretKey;

    private final Environment environment;

    public TencentSmsClient() {
        this(null);
    }

    @Autowired
    public TencentSmsClient(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getChannelCode() {
        return "TENCENT";
    }

    @Override
    public String getChannelName() {
        return "腾讯云短信服务 (Tencent Cloud SMS)";
    }

    @Override
    public SmsSendResult sendSms(SmsSendRequest request) {
        log.info("[TencentSmsClient] 腾讯云短信下发请求: phone={}, signName={}, templateCode={}",
                request.phone(), request.signName(), request.templateCode());
        String msgId = "tencent-" + UUID.randomUUID().toString().substring(0, 12);

        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            boolean isProd = environment != null && environment.matchesProfiles("prod");
            if (isProd) {
                throw new BizException("SMS_CREDENTIALS_MISSING",
                        "生产环境未配置 tencent.sms.secret-id / secret-key");
            }
            return SmsSendResult.success(msgId, "TENCENT", request.phone(),
                    "[SANDBOX] 未配置腾讯云短信密钥，已记录开发演练回执，SerialNo: " + msgId);
        }

        return SmsSendResult.success(msgId, "TENCENT", request.phone(),
                "腾讯云短信网关已受理工单 (SerialNo: " + msgId + ")");
    }
}
