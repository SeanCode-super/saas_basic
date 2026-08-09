package com.saasbasics.platform.modules.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DatasourceSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String type,
        @NotBlank String usageType,
        String host,
        Integer port,
        String databaseName,
        String username,
        @NotBlank String testStatus,
        @NotBlank String status,
        String remark
) {
}
