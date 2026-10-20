package com.saasbasics.platform.modules.integration.message;

import java.time.LocalDateTime;

/**
 * 邮件发送回执 DTO。
 */
public record MailSendResult(
        boolean success,
        String messageId,
        String to,
        String subject,
        String message,
        LocalDateTime sentAt
) {
    public static MailSendResult success(String messageId, String to, String subject, String message) {
        return new MailSendResult(true, messageId, to, subject, message, LocalDateTime.now());
    }

    public static MailSendResult failure(String to, String subject, String message) {
        return new MailSendResult(false, null, to, subject, message, LocalDateTime.now());
    }
}
