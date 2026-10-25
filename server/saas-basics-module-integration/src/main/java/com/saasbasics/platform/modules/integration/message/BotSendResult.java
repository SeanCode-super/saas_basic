package com.saasbasics.platform.modules.integration.message;

import java.time.LocalDateTime;

public record BotSendResult(
        boolean success,
        String botType,
        String messageId,
        String message,
        long durationMs,
        LocalDateTime sendTime
) {
    public static BotSendResult success(String botType, String messageId, String message, long durationMs) {
        return new BotSendResult(true, botType, messageId, message, durationMs, LocalDateTime.now());
    }

    public static BotSendResult failure(String botType, String errorMsg, long durationMs) {
        return new BotSendResult(false, botType, null, errorMsg, durationMs, LocalDateTime.now());
    }
}
