package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record UserStatusUpdateRequest(@NotBlank String status) {
}
