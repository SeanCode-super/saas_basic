package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;

public record AuthLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        LocalDateTime expireAt,
        AuthCurrentUserResponse currentUser
) {
    public AuthLoginResponse(String accessToken, String tokenType, LocalDateTime expireAt, AuthCurrentUserResponse currentUser) {
        this(accessToken, null, tokenType, expireAt, currentUser);
    }
}
