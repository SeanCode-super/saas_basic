package com.saasbasics.platform.modules.integration.message;

/**
 * 平台邮件发送统一 SPI 接口。
 */
public interface MailClient {

    /**
     * 执行邮件投递
     */
    MailSendResult sendMail(MailSendRequest request);
}
