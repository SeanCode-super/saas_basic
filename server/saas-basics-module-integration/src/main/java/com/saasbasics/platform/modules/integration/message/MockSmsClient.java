package com.saasbasics.platform.modules.integration.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 本地开发与测试沙箱模拟短信客户端。
 * 零配置即开即用，控制台打印下发验证码与参数，方便开发调试。
 */
@Component
public class MockSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(MockSmsClient.class);

    @Override
    public String getChannelCode() {
        return "MOCK";
    }

    @Override
    public String getChannelName() {
        return "本地沙箱模拟短信 (Mock SMS)";
    }

    @Override
    public SmsSendResult sendSms(SmsSendRequest request) {
        String msgId = "mock-sms-" + UUID.randomUUID().toString().substring(0, 8);
        int mockCode = ThreadLocalRandom.current().nextInt(100000, 999999);
        log.info("[MockSmsClient] 模拟下发短信: phone={}, signName={}, templateCode={}, params={}, mockCode={}",
                request.phone(), request.signName(), request.templateCode(), request.templateParams(), mockCode);
        return SmsSendResult.success(msgId, "MOCK", request.phone(), "模拟发送成功，验证码: " + mockCode);
    }
}
