package com.saasbasics.platform.modules.file.dto;

public record FileObjectResponse(
        Long id,
        Long tenantId,
        Long bucketId,
        String objectKey,
        String fileName,
        String fileExt,
        String contentType,
        Long fileSize,
        String visibility,
        String bizType,
        Integer versionNo,
        String status,
        String storagePath,
        String remark
) {
}
