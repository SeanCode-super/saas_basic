package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record AccessSubjectProfile(
        Long tenantId,
        Long userId,
        Long employeeId,
        Long departmentId,
        Long positionId,
        Long companyDepartmentId,
        List<Long> roleIds
) {
    public boolean matches(String subjectType, String subjectValue) {
        if (subjectType == null || subjectValue == null || subjectValue.isBlank()) {
            return false;
        }
        return switch (subjectType) {
            case "USER", "PERSONAL" -> matchesId(userId, subjectValue);
            case "DEPARTMENT" -> matchesId(departmentId, subjectValue);
            case "POSITION" -> matchesId(positionId, subjectValue);
            case "COMPANY" -> matchesId(companyDepartmentId, subjectValue);
            case "TENANT" -> matchesId(tenantId, subjectValue);
            case "ROLE" -> roleIds != null && roleIds.stream().anyMatch(id -> subjectValue.equals(String.valueOf(id)));
            default -> false;
        };
    }

    private boolean matchesId(Long id, String value) {
        return id != null && value.equals(String.valueOf(id));
    }
}
