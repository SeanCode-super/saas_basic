package com.saasbasics.platform.modules.system.dto;

public record NotifySendRequest(
        Long targetUserId,
        String category,
        String title,
        String content,
        String bizType,
        String bizId
) {
}
