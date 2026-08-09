package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FileUploadSessionCreateRequest(
        @NotNull Long tenantId,
        @NotNull Long bucketId,
        @NotNull Long storageId,
        @NotBlank String objectKey,
        @NotBlank String fileName,
        @NotNull @Min(1) Long fileSize,
        @NotBlank String uploadMode,
        @NotNull @Min(1) Integer partCount,
        @NotNull Long ownerUserId
) {
}
