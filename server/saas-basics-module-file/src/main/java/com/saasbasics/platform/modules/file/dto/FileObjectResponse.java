package com.saasbasics.platform.modules.file.dto;

public record FileObjectResponse(
        Long id,
        Long tenantId,
        Long bucketId,
        Long storageId,
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
        String uploadMode,
        String targetDir,
        String publicUrl,
        String remark
) {
}
