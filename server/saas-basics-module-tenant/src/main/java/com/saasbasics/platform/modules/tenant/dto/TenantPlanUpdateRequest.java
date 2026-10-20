package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotNull;

public record TenantPlanUpdateRequest(
        @NotNull Long packageId,
        String expireAt,
        String remark
) {
}
