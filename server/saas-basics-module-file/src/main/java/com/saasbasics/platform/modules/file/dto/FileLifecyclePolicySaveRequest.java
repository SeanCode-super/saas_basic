package com.saasbasics.platform.modules.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FileLifecyclePolicySaveRequest(
        @NotNull Long tenantId,
        @NotBlank String policyCode,
        @NotBlank String policyName,
        @NotBlank String fileScope,
        @NotNull Integer retentionDays,
        @NotNull Integer archiveAfterDays,
        @NotNull Integer deleteAfterDays,
        @NotNull Boolean deduplicateEnabled,
        @NotNull Integer versionRetentionCount,
        @NotBlank String status,
        String remark
) {
}
