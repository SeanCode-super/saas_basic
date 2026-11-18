package com.saasbasics.platform.modules.audit.dto;

import jakarta.validation.constraints.NotBlank;

public record BlacklistCreateRequest(
        @NotBlank(message = "封禁类型不能为空") String type,
        @NotBlank(message = "封禁目标值不能为空") String targetValue,
        String reason,
        Integer expireDays
) {
}
