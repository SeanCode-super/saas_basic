package com.saasbasics.platform.modules.iam.dto;

public record PositionResponse(
        Long id,
        Long tenantId,
        String positionCode,
        String positionName,
        String positionLevel,
        Long employeeCount,
        String status,
        Integer sortNo,
        String remark
) {
}
