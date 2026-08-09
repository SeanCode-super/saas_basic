package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemConfigSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String group,
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String value,
        @NotBlank String valueType,
        @NotBlank String status,
        String remark
) {
}
