package com.saasbasics.platform.modules.audit.dto;

import java.time.LocalDateTime;

public record LoginLogResponse(
        Long id,
        Long tenantId,
        Long userId,
        String username,
        String loginType,
        String loginIp,
        Boolean success,
        String failReason,
        LocalDateTime occurredAt
) {
}
