package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.saasbasics.platform.common.auth.OrganizationAccessContext;

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
        OrganizationAccessContext organizationContext
) {
}
