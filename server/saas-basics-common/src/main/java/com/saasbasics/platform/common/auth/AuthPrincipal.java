package com.saasbasics.platform.common.auth;

import java.time.LocalDateTime;
import java.util.List;

public record AuthPrincipal(
        Long sessionId,
        Long tenantId,
        String tenantCode,
        Long userId,
        String username,
        String nickname,
        String userType,
        String sessionStatus,
        LocalDateTime expireAt,
        List<String> permissions,
        List<String> featureFlags,
        List<Long> roleIds,
        List<String> buttonPermissions
) {
}
