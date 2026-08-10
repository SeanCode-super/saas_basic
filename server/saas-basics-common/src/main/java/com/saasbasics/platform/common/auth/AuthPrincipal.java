package com.saasbasics.platform.common.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        List<String> buttonPermissions,
        UUID sessionPublicId,
        UUID userPublicId,
        UUID subjectBindingPublicId,
        UUID personPublicId,
        OrganizationAccessContext organizationContext
) {
    public AuthPrincipal(Long sessionId,
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
                         List<String> buttonPermissions) {
        this(sessionId, tenantId, tenantCode, userId, username, nickname, userType, sessionStatus,
                expireAt, permissions, featureFlags, roleIds, buttonPermissions, null, null, null, null, null);
    }
}
