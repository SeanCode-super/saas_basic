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
        @com.saasbasics.platform.mask.DataMask(type = com.saasbasics.platform.common.mask.DataMaskType.MOBILE)
        String mobile,
        @com.saasbasics.platform.mask.DataMask(type = com.saasbasics.platform.common.mask.DataMaskType.EMAIL)
        String email,
        String gender,
        LocalDate hireDate,
        Long boundUserCount,
        String employeeStatus,
        String remark
) {
}
