package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        Long tenantId,
        String employeeNo,
        String employeeName,
        Long deptId,
        String deptName,
        String deptFullName,
        Long positionId,
        String positionName,
        String mobile,
        String email,
        String gender,
        LocalDate hireDate,
        Long boundUserCount,
        String employeeStatus,
        String remark
) {
}
