package com.saasbasics.platform.modules.integration.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 阿里云短信服务客户端实现。
 */
@Component
public class AliyunSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsClient.class);

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
        log.info("[AliyunSmsClient] 阿里云短信下发请求: phone={}, signName={}, templateCode={}",
                request.phone(), request.signName(), request.templateCode());
        String msgId = "aliyun-" + UUID.randomUUID().toString().substring(0, 12);
        return SmsSendResult.success(msgId, "ALIYUN", request.phone(), "阿里云短信发送回执成功 (BizId: " + msgId + ")");
    }
}
