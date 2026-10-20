package com.saasbasics.platform.modules.integration.message;

/**
 * 平台统一短信发送 SPI 接口。
 * 屏蔽阿里云、腾讯云、华为云、七牛云与 Mock 模拟渠道底层差异。
 */
public interface SmsClient {

    /**
     * 渠道标识，如 ALIYUN, TENCENT, MOCK
     */
    String getChannelCode();

    /**
     * 渠道名称展示
     */
    String getChannelName();

    /**
     * 执行短信发送
     */
    SmsSendResult sendSms(SmsSendRequest request);
}
