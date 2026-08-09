package com.saasbasics.platform.modules.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String jobType,
        @NotNull Long executorId,
        @NotBlank String handlerName,
        @NotBlank String status,
        String remark
) {
}
