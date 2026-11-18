package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantIsolationUpdateRequest(
        @NotBlank(message = "隔离模式不能为空") String isolationMode
) {
}
