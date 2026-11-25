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

    public boolean isSuperAdmin() {
        return hasPlatformAdminRole(tenantId, roleIds)
                || permissions != null && (permissions.contains("*") || permissions.contains("*:*:*"));
    }

    public static boolean hasPlatformAdminRole(Long tenantId, List<Long> roleIds) {
        return Long.valueOf(1L).equals(tenantId) && roleIds != null && roleIds.contains(1L);
    }

    /**
     * 校验当前主体是否属于平台超级运营总控租户（tenant_id = 1 或 tenant_code = platform）。
     */
    public boolean isPlatformTenant() {
        return Long.valueOf(1L).equals(tenantId) || "platform".equalsIgnoreCase(tenantCode);
    }
}
