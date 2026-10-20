package com.saasbasics.platform.modules.iam.dto;

public record SsoCallbackRequest(
        String channel, // WECOM / DINGTALK / FEISHU
        String authCode,
        String state,
        String tenantCode // optional, defaults to 'platform'
) {
}
