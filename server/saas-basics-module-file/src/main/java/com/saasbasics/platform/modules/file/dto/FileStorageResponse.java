package com.saasbasics.platform.modules.file.dto;

public record FileStorageResponse(
        Long id,
        Long tenantId,
        String storageCode,
        String storageName,
        String storageType,
        String endpoint,
        String bucketDefault,
        String regionName,
        String basePath,
        String defaultFolder,
        String pathPattern,
        Integer maxFileMb,
        String allowedExts,
        String publicBaseUrl,
        String status,
        Boolean isDefault,
        String uploadMode,
        String remark
) {
}
