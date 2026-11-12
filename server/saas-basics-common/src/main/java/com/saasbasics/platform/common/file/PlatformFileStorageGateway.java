package com.saasbasics.platform.common.file;

/**
 * Cross-module contract for checking storage source availability and storing uploaded files
 * according to configurable storage engines, upload modes, and business folder routing.
 */
public interface PlatformFileStorageGateway {

    StorageAvailability checkAvailability(Long tenantId, String bizType);

    StoredUploadResult uploadBytes(Long tenantId, String bizType, String originalFilename, String contentType, byte[] content);

    byte[] downloadObject(Long tenantId, String objectKey);

    record StorageAvailability(
            boolean configured,
            String reasonCode,
            String message,
            Long activeStorageId,
            String storageCode,
            String storageName,
            String storageType,
            String uploadMode,
            String basePath,
            String defaultFolder,
            String resolvedTargetDir,
            String resolvedPathPattern
    ) {
    }

    record StoredUploadResult(
            Long fileId,
            Long storageId,
            String storageCode,
            String storageType,
            String uploadMode,
            String targetDir,
            String objectKey,
            String url,
            String fileName,
            Long fileSize,
            String contentType
    ) {
    }
}
