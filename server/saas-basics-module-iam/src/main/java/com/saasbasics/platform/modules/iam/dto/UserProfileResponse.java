package com.saasbasics.platform.modules.iam.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String publicId,
        String userCode,
        Long tenantId,
        String tenantCode,
        String tenantName,
        String username,
        String nickname,
        String mobile,
        String email,
        Long avatarFileId,
        String avatarUrl,
        String gender,
        String bio,
        String workLocation,
        String extContact,
        String locale,
        String timezone,
        String userType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        LocalDateTime passwordChangedAt,
        Boolean needResetPassword,
        Integer passwordMaxAgeDays,
        Long passwordDaysRemaining,
        Boolean mfaEnabled,
        Integer securityScore,
        List<String> roles,
        List<DataScopeSummaryItem> dataScopes,
        List<IdentityBindingItem> identities,
        EmployeeProfileInfo employeeInfo,
        UserPreferencesData preferences,
        List<PersonalLoginLogItem> recentLogs
) {
    public record PersonalLoginLogItem(
            Long id,
            String loginIp,
            Boolean success,
            String failReason,
            LocalDateTime occurredAt
    ) {}

    public record DataScopeSummaryItem(
            String roleName,
            String scopeCode,
            String scopeName,
            String scopeType
    ) {}

    public record IdentityBindingItem(
            String identityType,
            String identifier,
            Boolean verified,
            String status,
            LocalDateTime boundAt
    ) {}

    public record AssignmentItem(
            String assignmentPublicId,
            String organizationName,
            String orgUnitName,
            String positionName,
            boolean primary,
            boolean active
    ) {}

    public record EmployeeProfileInfo(
            Long employeeId,
            String employeeNo,
            String employeeName,
            Long deptId,
            String deptName,
            String deptFullPath,
            Long positionId,
            String positionName,
            LocalDate hireDate,
            String employeeStatus,
            String personPublicId,
            String activeAssignmentPublicId,
            List<AssignmentItem> assignments
    ) {}
}
