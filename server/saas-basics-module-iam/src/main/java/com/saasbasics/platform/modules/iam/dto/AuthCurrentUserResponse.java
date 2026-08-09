package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthCurrentUserResponse(
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
