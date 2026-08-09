package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TenantSaveRequest(
        @NotBlank String tenantCode,
        @NotBlank String tenantName,
        @NotNull Long packageId,
        @NotBlank String status,
        @NotBlank String isolationMode,
        String remark
) {
}
