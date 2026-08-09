package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotNull;

public record EmployeeTransferRequest(
        @NotNull Long deptId,
        @NotNull Long positionId,
        String remark
) {
}
