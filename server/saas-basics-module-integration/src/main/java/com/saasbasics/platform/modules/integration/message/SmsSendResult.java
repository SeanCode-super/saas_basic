package com.saasbasics.platform.modules.integration.message;

import java.time.LocalDateTime;

/**
 * 短信发送统一回执。
 */
public record SmsSendResult(
        boolean success,
        String messageId,
        String channelCode,
        String phone,
        String code,
        String message,
        LocalDateTime sentAt
) {
    public static SmsSendResult success(String messageId, String channelCode, String phone, String message) {
        return new SmsSendResult(true, messageId, channelCode, phone, "OK", message, LocalDateTime.now());
    }

    public static SmsSendResult failure(String channelCode, String phone, String code, String message) {
        return new SmsSendResult(false, null, channelCode, phone, code, message, LocalDateTime.now());
    }
}
