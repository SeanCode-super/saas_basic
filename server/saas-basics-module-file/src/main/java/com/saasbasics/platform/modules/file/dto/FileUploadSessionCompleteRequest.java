package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FileUploadSessionCompleteRequest(
        @NotNull Long tenantId,
        @NotBlank String contentType,
        @NotBlank String visibility,
        String bizType,
        String remark
) {
}
