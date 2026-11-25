package com.saasbasics.platform.modules.iam.dto;

import java.util.List;
import java.util.UUID;

public record AccessSubjectProfile(
        Long tenantId,
        Long userId,
        Long employeeId,
        Long departmentId,
        Long positionId,
        Long companyDepartmentId,
        List<Long> roleIds,
        UUID personPublicId,
        UUID organizationPublicId,
        UUID orgUnitPublicId,
        UUID assignmentPublicId,
        boolean legacyFallbackUsed
) {
    public AccessSubjectProfile(Long tenantId,
                                Long userId,
                                Long employeeId,
                                Long departmentId,
                                Long positionId,
                                Long companyDepartmentId,
                                List<Long> roleIds) {
        this(tenantId, userId, employeeId, departmentId, positionId, companyDepartmentId, roleIds,
                null, null, null, null, employeeId != null);
    }

    public boolean matches(String subjectType, String subjectValue) {
        if (subjectType == null || subjectValue == null || subjectValue.isBlank()) {
            return false;
        }
        return switch (subjectType) {
            case "USER", "PERSONAL" -> matchesId(userId, subjectValue);
            case "PERSON" -> matchesUuid(personPublicId, subjectValue);
            case "ORGANIZATION" -> matchesUuid(organizationPublicId, subjectValue);
            case "ORG_UNIT" -> matchesUuid(orgUnitPublicId, subjectValue);
            case "ASSIGNMENT" -> matchesUuid(assignmentPublicId, subjectValue);
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

    private boolean matchesUuid(UUID id, String value) {
        return id != null && value.equalsIgnoreCase(id.toString());
    }
}
