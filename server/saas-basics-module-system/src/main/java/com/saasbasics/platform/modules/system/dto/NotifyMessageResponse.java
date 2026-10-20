package com.saasbasics.platform.modules.system.dto;

import java.time.LocalDateTime;

public record NotifyMessageResponse(
        Long id,
        Long tenantId,
        Long userId,
        String category,
        String title,
        String content,
        Integer readStatus,
        LocalDateTime readAt,
        String bizType,
        String bizId,
        String extJson,
        LocalDateTime createdAt
) {
}
