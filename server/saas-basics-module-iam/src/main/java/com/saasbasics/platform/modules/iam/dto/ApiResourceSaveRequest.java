package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApiResourceSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String resourceCode,
        @NotBlank String resourceName,
        @NotBlank String httpMethod,
        @NotBlank String urlPattern,
        @NotNull Boolean authRequired,
        @NotBlank String status,
        String remark
) {
}
