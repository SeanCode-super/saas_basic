package com.saasbasics.platform.modules.scheduler.dto;

import jakarta.validation.constraints.NotNull;

public record JobTriggerRequest(
        @NotNull Long tenantId,
        String remark
) {
}
