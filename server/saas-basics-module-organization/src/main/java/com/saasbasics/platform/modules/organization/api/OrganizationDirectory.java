package com.saasbasics.platform.modules.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stable cross-module read contract. Internal identifiers and persistence types never cross this boundary. */
public interface OrganizationDirectory {

    OrganizationSummary getOrganization(UUID organizationPublicId);

    PersonSummary getPerson(UUID personPublicId);

    PersonSummary requireEffectivePerson(UUID personPublicId, Instant effectiveAt);

    List<EngagementSummary> findEffectiveEngagements(UUID personPublicId, Instant effectiveAt);

    List<AssignmentSummary> findEffectiveAssignments(UUID personPublicId, Instant effectiveAt);

    ContextSelection validateContext(ContextSelection selection, Instant effectiveAt);

    AssignmentContext requireEffectiveAssignmentForPerson(
            UUID personPublicId,
            UUID assignmentPublicId,
            Instant effectiveAt
    );

    record OrganizationSummary(UUID publicId, String code, String displayName) {
    }

    record PersonSummary(
            UUID publicId,
            String code,
            String displayName,
            String status,
            Instant validFrom,
            Instant validTo
    ) {
    }

    record EngagementSummary(
            UUID publicId,
            UUID personPublicId,
            UUID organizationPublicId,
            String typeNamespace,
            String typeCode,
            String typeVersion,
            Instant validFrom,
            Instant validTo
    ) {
    }

    record AssignmentSummary(
            UUID publicId,
            UUID engagementPublicId,
            UUID organizationPublicId,
            UUID orgUnitPublicId,
            UUID positionPublicId,
            boolean primary,
            Instant validFrom,
            Instant validTo
    ) {
    }

    record ContextSelection(
            UUID organizationPublicId,
            UUID engagementPublicId,
            UUID orgUnitPublicId,
            UUID positionPublicId,
            UUID assignmentPublicId
    ) {
    }

    record AssignmentContext(
            UUID personPublicId,
            UUID organizationPublicId,
            UUID engagementPublicId,
            UUID orgUnitPublicId,
            UUID positionPublicId,
            UUID assignmentPublicId
    ) {
    }
}
