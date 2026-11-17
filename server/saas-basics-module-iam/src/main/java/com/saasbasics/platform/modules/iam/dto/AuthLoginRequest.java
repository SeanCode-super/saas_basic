package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        String tenantCode,
        String clientId,
        String terminalCode,
        String captchaId,
        String captchaCode,
        String totpCode,
        @NotBlank(message = "请输入用户名")
        String username,
        @NotBlank(message = "请输入密码")
        String password
) {
    public AuthLoginRequest(
            String tenantCode,
            String clientId,
            String terminalCode,
            String captchaCode,
            String username,
            String password) {
        this(tenantCode, clientId, terminalCode, null, captchaCode, null, username, password);
    }
}
