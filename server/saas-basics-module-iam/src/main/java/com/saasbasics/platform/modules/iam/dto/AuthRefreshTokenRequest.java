package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshTokenRequest(
        @NotBlank(message = "refreshToken不得为空")
        String refreshToken
) {
}
