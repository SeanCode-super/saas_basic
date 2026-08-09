package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "请输入租户编码")
        String tenantCode,
        String clientId,
        String terminalCode,
        String captchaCode,
        @NotBlank(message = "请输入用户名")
        String username,
        @NotBlank(message = "请输入密码")
        String password
) {
}
