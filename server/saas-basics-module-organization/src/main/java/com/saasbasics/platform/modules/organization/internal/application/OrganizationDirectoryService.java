package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AssignmentEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.EngagementEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgUnitEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PersonEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PositionEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrganizationDirectoryService implements OrganizationDirectory {

    private final OrganizationMapperAccess mappers;
    private final OrganizationEntitySupport support;
    private final OrganizationCatalogService catalog;
    private final OrganizationStructureService structure;
    private final OrganizationWorkforceService workforce;

    public OrganizationDirectoryService(OrganizationMapperAccess mappers,
                                        OrganizationEntitySupport support,
                                        OrganizationCatalogService catalog,
                                        OrganizationStructureService structure,
                                        OrganizationWorkforceService workforce) {
        this.mappers = mappers;
        this.support = support;
        this.catalog = catalog;
        this.structure = structure;
        this.workforce = workforce;
    }

    @Override
    public OrganizationSummary getOrganization(UUID organizationPublicId) {
        OrganizationEntity entity = catalog.organization(organizationPublicId);
        return new OrganizationSummary(support.toUuid(entity.getPublicId()), entity.getOrganizationCode(), entity.getDisplayName());
    }

    @Override
    public List<EngagementSummary> findEffectiveEngagements(UUID personPublicId, Instant effectiveAt) {
        requireEffectiveAt(effectiveAt);
        PersonEntity person = workforce.person(personPublicId);
        return mappers.engagements().selectList(this.<EngagementEntity>effectiveQuery(effectiveAt)
                        .eq("person_id", person.getId())
                        .orderByAsc("organization_id", "valid_from", "id"))
                .stream()
                .map(this::toEngagementSummary)
                .toList();
    }

    @Override
    public List<AssignmentSummary> findEffectiveAssignments(UUID personPublicId, Instant effectiveAt) {
        requireEffectiveAt(effectiveAt);
        List<Long> engagementIds = findEffectiveEngagementEntities(personPublicId, effectiveAt)
                .stream()
                .map(EngagementEntity::getId)
                .toList();
        if (engagementIds.isEmpty()) {
            return List.of();
        }
        return mappers.assignments().selectList(this.<AssignmentEntity>effectiveQuery(effectiveAt)
                        .in("engagement_id", engagementIds)
                        .orderByDesc("is_primary")
                        .orderByAsc("valid_from", "id"))
                .stream()
                .map(this::toAssignmentSummary)
                .toList();
    }

    @Override
    public ContextSelection validateContext(ContextSelection selection, Instant effectiveAt) {
        if (selection == null) {
            throw new BizException("ORG_CONTEXT_REQUIRED", "An organization context selection is required");
        }
        requireEffectiveAt(effectiveAt);
        AssignmentEntity assignment = workforce.assignment(selection.assignmentPublicId());
        EngagementEntity engagement = workforce.engagement(assignment.getEngagementId());
        OrgUnitEntity unit = structure.unit(assignment.getOrgUnitId());
        PositionEntity position = structure.position(assignment.getPositionId());
        OrganizationEntity organization = catalog.organization(assignment.getOrganizationId());

        boolean identifiersMatch = Objects.equals(selection.organizationPublicId(), support.toUuid(organization.getPublicId()))
                && Objects.equals(selection.engagementPublicId(), support.toUuid(engagement.getPublicId()))
                && Objects.equals(selection.orgUnitPublicId(), support.toUuid(unit.getPublicId()))
                && Objects.equals(selection.positionPublicId(), support.toUuid(position.getPublicId()));
        if (!identifiersMatch || !isEffective(assignment, effectiveAt) || !isEffective(engagement, effectiveAt)
                || !isEffective(unit, effectiveAt) || !isEffective(position, effectiveAt) || !isEffective(organization, effectiveAt)) {
            throw new BizException("ORG_CONTEXT_INVALID", "The selected organization context is not effective or internally consistent");
        }
        return selection;
    }

    private List<EngagementEntity> findEffectiveEngagementEntities(UUID personPublicId, Instant effectiveAt) {
        PersonEntity person = workforce.person(personPublicId);
        return mappers.engagements().selectList(this.<EngagementEntity>effectiveQuery(effectiveAt)
                .eq("person_id", person.getId())
                .orderByAsc("valid_from", "id"));
    }

    private <E> QueryWrapper<E> effectiveQuery(Instant effectiveAt) {
        LocalDateTime at = LocalDateTime.ofInstant(effectiveAt, ZoneOffset.UTC);
        return new QueryWrapper<E>()
                .eq("deleted", 0)
                .eq("status", LifecycleStatus.ACTIVE.name())
                .le("valid_from", at)
                .and(query -> query.isNull("valid_to").or().gt("valid_to", at));
    }

    private EngagementSummary toEngagementSummary(EngagementEntity entity) {
        PersonEntity person = workforce.person(entity.getPersonId());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        return new EngagementSummary(
                support.toUuid(entity.getPublicId()),
                support.toUuid(person.getPublicId()),
                support.toUuid(organization.getPublicId()),
                entity.getTypeNamespace(),
                entity.getTypeCode(),
                entity.getTypeVersion(),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo())
        );
    }

    private AssignmentSummary toAssignmentSummary(AssignmentEntity entity) {
        EngagementEntity engagement = workforce.engagement(entity.getEngagementId());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        OrgUnitEntity unit = structure.unit(entity.getOrgUnitId());
        PositionEntity position = structure.position(entity.getPositionId());
        return new AssignmentSummary(
                support.toUuid(entity.getPublicId()),
                support.toUuid(engagement.getPublicId()),
                support.toUuid(organization.getPublicId()),
                support.toUuid(unit.getPublicId()),
                support.toUuid(position.getPublicId()),
                Boolean.TRUE.equals(entity.getPrimaryAssignment()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo())
        );
    }

    private boolean isEffective(com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity entity,
                                Instant effectiveAt) {
        return LifecycleStatus.valueOf(entity.getStatus()) == LifecycleStatus.ACTIVE
                && support.period(entity).contains(effectiveAt);
    }

    private void requireEffectiveAt(Instant effectiveAt) {
        if (effectiveAt == null) {
            throw new BizException("ORG_EFFECTIVE_AT_REQUIRED", "effectiveAt is required");
        }
    }
}
