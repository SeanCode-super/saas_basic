package com.saasbasics.platform.modules.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(
        @NotNull Long tenantId,
        @NotBlank String status
) {
}
