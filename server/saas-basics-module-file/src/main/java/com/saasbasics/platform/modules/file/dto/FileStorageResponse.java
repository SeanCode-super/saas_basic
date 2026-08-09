package com.saasbasics.platform.modules.file.dto;

public record FileStorageResponse(
        Long id,
        Long tenantId,
        String storageCode,
        String storageName,
        String storageType,
        String endpoint,
        String bucketDefault,
        String publicBaseUrl,
        String status,
        String remark
) {
}
