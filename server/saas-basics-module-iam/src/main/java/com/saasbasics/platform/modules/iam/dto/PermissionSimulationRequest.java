package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotNull;

public record PermissionSimulationRequest(
        @NotNull Long userId,
        String actionCode,
        String menuCode,
        Long targetDepartmentId
) {
}
