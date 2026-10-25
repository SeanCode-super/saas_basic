package com.saasbasics.platform.modules.integration.message;

import jakarta.validation.constraints.NotBlank;

public record BotSendRequest(
        @NotBlank(message = "机器人渠道不能为空 (WECHAT_WORK / DINGTALK / FEISHU)")
        String botType,
        @NotBlank(message = "Webhook 地址不能为空")
        String webhookUrl,
        String secret,
        @NotBlank(message = "消息标题不能为空")
        String title,
        @NotBlank(message = "消息内容不能为空")
        String content,
        String msgType
) {
}
