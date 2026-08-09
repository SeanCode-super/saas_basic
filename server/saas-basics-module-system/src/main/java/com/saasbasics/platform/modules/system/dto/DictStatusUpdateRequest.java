package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DictStatusUpdateRequest(
        @NotNull Long tenantId,
        @NotBlank String status
) {
}
