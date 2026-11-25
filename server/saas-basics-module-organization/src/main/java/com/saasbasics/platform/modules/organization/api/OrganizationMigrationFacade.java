package com.saasbasics.platform.modules.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repeatable legacy-to-standard organization migration contract backed by V26 migration control tables.
 */
public interface OrganizationMigrationFacade {

    enum MigrationMode {
        VALIDATE,
        APPLY,
        VERIFY
    }

    record LegacyDepartmentInput(
            long legacyId,
            Long parentLegacyId,
            String code,
            String name,
            Integer sortOrder,
            boolean active
    ) {
    }

    record LegacyPositionInput(
            long legacyId,
            String code,
            String name,
            boolean active
    ) {
    }

    record LegacyEmployeeInput(
            long legacyId,
            String employeeNo,
            String employeeName,
            Long legacyDepartmentId,
            Long legacyPositionId,
            Instant effectiveFrom,
            boolean active
    ) {
    }

    record LegacyOrganizationSnapshot(
            long tenantId,
            String tenantCode,
            String organizationCode,
            String organizationName,
            List<LegacyDepartmentInput> departments,
            List<LegacyPositionInput> positions,
            List<LegacyEmployeeInput> employees
    ) {
    }

    record IssueSummary(
            String resourceType,
            String resourceKey,
            String issueCode,
            String severity,
            String detail
    ) {
    }

    record MigrationReport(
            UUID runPublicId,
            UUID scopePublicId,
            MigrationMode mode,
            String status,
            int sourceResourceCount,
            int mappedResourceCount,
            int blockingIssueCount,
            int warningIssueCount,
            UUID organizationPublicId,
            Map<Long, UUID> employeeToPersonPublicIds,
            List<IssueSummary> issues
    ) {
    }

    MigrationReport execute(LegacyOrganizationSnapshot snapshot, MigrationMode mode);
}
