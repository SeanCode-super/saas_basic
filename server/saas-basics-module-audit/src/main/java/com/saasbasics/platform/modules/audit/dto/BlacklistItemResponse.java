package com.saasbasics.platform.modules.audit.dto;

import java.time.LocalDateTime;

public record BlacklistItemResponse(
        Long id,
        Long tenantId,
        String type,
        String targetValue,
        String reason,
        LocalDateTime blockedAt,
        LocalDateTime expireAt,
        String status,
        String operator
) {
}
