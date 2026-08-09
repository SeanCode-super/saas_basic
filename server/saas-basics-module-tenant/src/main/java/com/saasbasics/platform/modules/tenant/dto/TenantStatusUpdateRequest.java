package com.saasbasics.platform.modules.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantStatusUpdateRequest(@NotBlank String status) {
}
