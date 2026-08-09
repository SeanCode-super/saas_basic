package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FileStorageSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String storageCode,
        @NotBlank String storageName,
        @NotBlank String storageType,
        String endpoint,
        String bucketDefault,
        String publicBaseUrl,
        @NotBlank String status,
        String remark
) {
}
