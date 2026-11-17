package com.saasbasics.platform.modules.file.dto;

public record FileUploadRouteResponse(
        Long id,
        Long tenantId,
        Long storageId,
        String bizType,
        String routeName,
        String uploadMode,
        String targetDir,
        String pathPattern,
        Integer maxFileMb,
        String allowedExts,
        Integer priority,
        String status,
        String remark
) {
}
