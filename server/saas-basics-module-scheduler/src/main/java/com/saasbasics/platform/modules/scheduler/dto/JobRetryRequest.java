package com.saasbasics.platform.modules.scheduler.dto;

import jakarta.validation.constraints.NotNull;

public record JobRetryRequest(
        @NotNull Long tenantId,
        String remark
) {
}
