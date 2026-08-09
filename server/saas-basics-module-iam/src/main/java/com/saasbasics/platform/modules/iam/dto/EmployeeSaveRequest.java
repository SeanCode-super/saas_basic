package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EmployeeSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String employeeNo,
        @NotBlank String employeeName,
        @NotNull Long deptId,
        @NotNull Long positionId,
        String mobile,
        String email,
        String gender,
        LocalDate hireDate,
        @NotBlank String employeeStatus,
        String remark
) {
}
