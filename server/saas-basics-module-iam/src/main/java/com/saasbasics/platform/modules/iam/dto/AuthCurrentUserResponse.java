package com.saasbasics.platform.modules.iam.dto;

import com.saasbasics.platform.common.auth.OrganizationAccessContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        List<String> buttonPermissions,
        UUID sessionPublicId,
        UUID userPublicId,
        UUID subjectBindingPublicId,
        UUID personPublicId,
        OrganizationAccessContext organizationContext,
        String avatarUrl,
        String locale,
        String timezone,
        UserPreferencesData preferences
) {
    public AuthCurrentUserResponse(
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
            List<String> buttonPermissions,
            UUID sessionPublicId,
            UUID userPublicId,
            UUID subjectBindingPublicId,
            UUID personPublicId,
            OrganizationAccessContext organizationContext
    ) {
        this(
                sessionId,
                tenantId,
                tenantCode,
                userId,
                username,
                nickname,
                userType,
                sessionStatus,
                expireAt,
                permissions,
                featureFlags,
                roleIds,
                buttonPermissions,
                sessionPublicId,
                userPublicId,
                subjectBindingPublicId,
                personPublicId,
                organizationContext,
                null,
                "zh-CN",
                "Asia/Shanghai",
                UserPreferencesData.defaults("zh-CN", "Asia/Shanghai")
        );
    }
}
