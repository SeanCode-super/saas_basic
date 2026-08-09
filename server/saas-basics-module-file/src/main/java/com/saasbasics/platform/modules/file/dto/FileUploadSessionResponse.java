package com.saasbasics.platform.modules.file.dto;

import java.time.LocalDateTime;

public record FileUploadSessionResponse(
        Long id,
        Long tenantId,
        Long bucketId,
        Long storageId,
        String sessionCode,
        String objectKey,
        String fileName,
        Long fileSize,
        String uploadMode,
        Integer partCount,
        Long ownerUserId,
        String status,
        LocalDateTime expireAt,
        LocalDateTime completedAt
) {
}
