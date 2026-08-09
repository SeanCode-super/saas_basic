package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PositionSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String positionCode,
        @NotBlank String positionName,
        String positionLevel,
        @NotBlank String status,
        Integer sortNo,
        String remark
) {
}
