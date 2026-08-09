package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FileAccessLogCreateRequest(
        @NotNull Long tenantId,
        @NotNull Long fileId,
        @NotBlank String accessType,
        @NotNull Long operatorUserId,
        String operatorIp,
        @NotNull Boolean success,
        String remark
) {
}
