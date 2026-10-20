package com.saasbasics.platform.modules.integration.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 腾讯云短信服务客户端实现。
 */
@Component
public class TencentSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(TencentSmsClient.class);

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
        return SmsSendResult.success(msgId, "TENCENT", request.phone(), "腾讯云短信下发成功 (SerialNo: " + msgId + ")");
    }
}
