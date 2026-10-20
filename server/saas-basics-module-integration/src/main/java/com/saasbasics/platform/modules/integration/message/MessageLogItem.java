package com.saasbasics.platform.modules.integration.message;

import java.time.LocalDateTime;

/**
 * 消息下发审计台账模型。
 */
public record MessageLogItem(
        String id,
        String type, // SMS / MAIL
        String target, // 手机号或邮箱
        String channel,
        String title, // 签名或邮件主题
        String content, // 内容摘要
        String status, // SUCCESS / FAILED
        String messageId,
        String responseMsg,
        long costMillis,
        LocalDateTime sentAt
) {}
