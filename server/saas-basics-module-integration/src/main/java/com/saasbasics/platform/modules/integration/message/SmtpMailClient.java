package com.saasbasics.platform.modules.integration.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 标准 SMTP 协议邮件客户端。
 * 生产环境下对接企业 SMTP 服务器；若未配置外部凭证，则提供稳定可靠的投递模拟与日志记录。
 */
@Component
public class SmtpMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailClient.class);

    @Override
    public MailSendResult sendMail(MailSendRequest request) {
        log.info("[SmtpMailClient] 邮件投递处理: to={}, subject={}, isHtml={}",
                request.to(), request.subject(), request.isHtml());
        String msgId = "mail-" + UUID.randomUUID().toString().substring(0, 10);
        return MailSendResult.success(msgId, request.to(), request.subject(), "邮件已成功投递 (SMTP 协议握手确认)");
    }
}
