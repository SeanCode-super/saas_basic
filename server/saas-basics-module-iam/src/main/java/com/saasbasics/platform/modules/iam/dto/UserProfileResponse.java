package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        Long tenantId,
        String tenantCode,
        String username,
        String nickname,
        String mobile,
        String email,
        String userType,
        String status,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        List<String> roles,
        List<PersonalLoginLogItem> recentLogs
) {
    public record PersonalLoginLogItem(
            Long id,
            String loginIp,
            Boolean success,
            String failReason,
            LocalDateTime occurredAt
    ) {}
}
