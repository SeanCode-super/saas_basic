package com.saasbasics.platform.modules.integration.message;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 短信发送请求 DTO。
 */
public record SmsSendRequest(
        @NotBlank(message = "手机号不能为空") String phone,
        String channelCode,
        String templateCode,
        String signName,
        Map<String, Object> templateParams
) {}
