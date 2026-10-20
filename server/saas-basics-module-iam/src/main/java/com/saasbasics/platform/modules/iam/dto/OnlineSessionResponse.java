package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;

public record OnlineSessionResponse(
        String sessionNo,
        Long userId,
        String username,
        String nickname,
        String userType,
        String loginIp,
        String userAgent,
        LocalDateTime loginTime,
        LocalDateTime lastAccessAt,
        LocalDateTime expireAt,
        String status,
        boolean isCurrent
) {
}
