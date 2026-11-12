package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.NotBlank;

public record FileUploadRouteSaveRequest(
        Long tenantId,
        Long storageId,
        @NotBlank String bizType,
        @NotBlank String routeName,
        String uploadMode,
        @NotBlank String targetDir,
        String pathPattern,
        Integer maxFileMb,
        String allowedExts,
        Integer priority,
        String status,
        String remark
) {
}
