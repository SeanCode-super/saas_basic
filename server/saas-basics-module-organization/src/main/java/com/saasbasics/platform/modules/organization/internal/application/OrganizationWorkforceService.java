package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.organization.api.model.AssignmentModels;
import com.saasbasics.platform.modules.organization.api.model.EngagementModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.api.model.PersonModels;
import com.saasbasics.platform.modules.organization.domain.model.EffectivePeriod;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.domain.model.OrganizationRules;
import com.saasbasics.platform.modules.organization.domain.model.TypeDescriptor;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AssignmentEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.EngagementEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgUnitEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PersonEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PositionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationWorkforceService {

    private static final String PERSON_NOT_FOUND = "ORG_PERSON_NOT_FOUND";
    private static final String ENGAGEMENT_NOT_FOUND = "ORG_ENGAGEMENT_NOT_FOUND";
    private static final String ASSIGNMENT_NOT_FOUND = "ORG_ASSIGNMENT_NOT_FOUND";

    private final OrganizationMapperAccess mappers;
    private final OrganizationEntitySupport support;
    private final OrganizationCatalogService catalog;
    private final OrganizationStructureService structure;
    private final AuditRecorder auditTrail;

    public OrganizationWorkforceService(OrganizationMapperAccess mappers,
                                        OrganizationEntitySupport support,
                                        OrganizationCatalogService catalog,
                                        OrganizationStructureService structure,
                                        AuditRecorder auditTrail) {
        this.mappers = mappers;
        this.support = support;
        this.catalog = catalog;
        this.structure = structure;
        this.auditTrail = auditTrail;
    }

    public PageResponse<PersonModels.Response> listPersons(int page, int size) {
        Page<PersonEntity> result = mappers.persons().selectPage(
                Page.of(page, size),
                new QueryWrapper<PersonEntity>().eq("deleted", 0).orderByAsc("person_code", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toPersonResponse).toList(), result.getTotal());
    }

    public PersonModels.Response getPerson(UUID publicId) {
        return toPersonResponse(person(publicId));
    }

    @Transactional
    public PersonModels.Response createPerson(PersonModels.CreateRequest request) {
        PersonEntity entity = new PersonEntity();
        entity.setPersonCode(OrganizationRules.requireResourceCode(request.personCode(), "personCode"));
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.persons().insert(entity), "ORG_PERSON_CODE_DUPLICATE");
        audit(entity, "CREATE", entity.getPersonCode());
        return toPersonResponse(entity);
    }

    @Transactional
    public PersonModels.Response updatePerson(UUID publicId, PersonModels.UpdateRequest request) {
        PersonEntity entity = person(publicId);
        requireNotArchived(entity);
        String code = OrganizationRules.requireResourceCode(request.personCode(), "personCode");
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        boolean identityChanged = !Objects.equals(code, entity.getPersonCode()) || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setPersonCode(code);
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.persons(), entity, request.expectedVersion()), "ORG_PERSON_CODE_DUPLICATE");
        audit(entity, "UPDATE", code);
        return toPersonResponse(entity);
    }

    @Transactional
    public PersonModels.Response transitionPerson(UUID publicId, LifecycleTransitionRequest request) {
        PersonEntity entity = person(publicId);
        if (request.targetStatus() == LifecycleStatus.INACTIVE) {
            if (count(mappers.engagements(), "person_id", entity.getId(), true) > 0) {
                throw new BizException("ORG_PERSON_ACTIVE_ENGAGEMENTS", "Active engagements must be deactivated first");
            }
        }
        if (request.targetStatus() == LifecycleStatus.ARCHIVED
                && countUnarchived(mappers.engagements(), "person_id", entity.getId()) > 0) {
            throw new BizException("ORG_PERSON_UNARCHIVED_ENGAGEMENTS", "Engagements must be archived first");
        }
        if (support.transition(mappers.persons(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toPersonResponse(entity);
    }

    @Transactional
    public void deletePerson(UUID publicId, int expectedVersion) {
        PersonEntity entity = person(publicId);
        if (count(mappers.engagements(), "person_id", entity.getId(), false) > 0) {
            throw new BizException("ORG_PERSON_REFERENCED", "Referenced persons cannot be deleted");
        }
        support.softDelete(mappers.persons(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getPersonCode());
    }

    public PageResponse<EngagementModels.Response> listEngagements(int page, int size) {
        Page<EngagementEntity> result = mappers.engagements().selectPage(
                Page.of(page, size),
                new QueryWrapper<EngagementEntity>().eq("deleted", 0).orderByAsc("engagement_code", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toEngagementResponse).toList(), result.getTotal());
    }

    public EngagementModels.Response getEngagement(UUID publicId) {
        return toEngagementResponse(engagement(publicId));
    }

    @Transactional
    public EngagementModels.Response createEngagement(EngagementModels.CreateRequest request) {
        PersonEntity person = person(request.personPublicId());
        OrganizationEntity organization = catalog.organization(request.organizationPublicId());
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(person), "Engagement");
        requireWithin(period, support.period(organization), "Engagement");

        EngagementEntity entity = new EngagementEntity();
        entity.setEngagementCode(OrganizationRules.requireResourceCode(request.engagementCode(), "engagementCode"));
        entity.setPersonId(person.getId());
        entity.setOrganizationId(organization.getId());
        support.applyType(entity, request.type().toDomain());
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.engagements().insert(entity), "ORG_ENGAGEMENT_CODE_DUPLICATE");
        audit(entity, "CREATE", entity.getEngagementCode());
        return toEngagementResponse(entity);
    }

    @Transactional
    public EngagementModels.Response updateEngagement(UUID publicId, EngagementModels.UpdateRequest request) {
        EngagementEntity entity = engagement(publicId);
        requireNotArchived(entity);
        String code = OrganizationRules.requireResourceCode(request.engagementCode(), "engagementCode");
        TypeDescriptor type = request.type().toDomain();
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(person(entity.getPersonId())), "Engagement");
        requireWithin(period, support.period(catalog.organization(entity.getOrganizationId())), "Engagement");
        boolean identityChanged = !Objects.equals(code, entity.getEngagementCode())
                || typeChanged(entity, type)
                || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setEngagementCode(code);
        support.applyType(entity, type);
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.engagements(), entity, request.expectedVersion()), "ORG_ENGAGEMENT_CODE_DUPLICATE");
        audit(entity, "UPDATE", code);
        return toEngagementResponse(entity);
    }

    @Transactional
    public EngagementModels.Response transitionEngagement(UUID publicId, LifecycleTransitionRequest request) {
        EngagementEntity entity = engagement(publicId);
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            PersonEntity person = person(entity.getPersonId());
            OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
            requireActive(person, "Person");
            requireActive(organization, "Organization");
            requireWithin(support.period(entity), support.period(person), "Engagement");
            requireWithin(support.period(entity), support.period(organization), "Engagement");
        }
        if (request.targetStatus() == LifecycleStatus.INACTIVE) {
            if (count(mappers.assignments(), "engagement_id", entity.getId(), true) > 0) {
                throw new BizException("ORG_ENGAGEMENT_ACTIVE_ASSIGNMENTS", "Active assignments must be terminated first");
            }
        }
        if (request.targetStatus() == LifecycleStatus.ARCHIVED
                && countUnarchived(mappers.assignments(), "engagement_id", entity.getId()) > 0) {
            throw new BizException("ORG_ENGAGEMENT_UNARCHIVED_ASSIGNMENTS", "Assignments must be archived first");
        }
        if (support.transition(mappers.engagements(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toEngagementResponse(entity);
    }

    @Transactional
    public void deleteEngagement(UUID publicId, int expectedVersion) {
        EngagementEntity entity = engagement(publicId);
        if (count(mappers.assignments(), "engagement_id", entity.getId(), false) > 0) {
            throw new BizException("ORG_ENGAGEMENT_REFERENCED", "Referenced engagements cannot be deleted");
        }
        support.softDelete(mappers.engagements(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getEngagementCode());
    }

    public PageResponse<AssignmentModels.Response> listAssignments(int page, int size) {
        Page<AssignmentEntity> result = mappers.assignments().selectPage(
                Page.of(page, size),
                new QueryWrapper<AssignmentEntity>().eq("deleted", 0).orderByAsc("engagement_id", "valid_from", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toAssignmentResponse).toList(), result.getTotal());
    }

    public AssignmentModels.Response getAssignment(UUID publicId) {
        return toAssignmentResponse(assignment(publicId));
    }

    @Transactional
    public AssignmentModels.Response createAssignment(AssignmentModels.CreateRequest request) {
        EngagementEntity engagement = engagement(request.engagementPublicId());
        OrgUnitEntity unit = structure.unit(request.orgUnitPublicId());
        PositionEntity position = structure.position(request.positionPublicId());
        validateAssignmentReferences(engagement, unit, position);
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        validateAssignmentPeriod(period, engagement, unit, position);

        AssignmentEntity entity = new AssignmentEntity();
        entity.setEngagementId(engagement.getId());
        entity.setOrganizationId(engagement.getOrganizationId());
        entity.setOrgUnitId(unit.getId());
        entity.setPositionId(position.getId());
        entity.setPrimaryAssignment(Boolean.TRUE.equals(request.primary()));
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.assignments().insert(entity), "ORG_ASSIGNMENT_DUPLICATE");
        audit(entity, "CREATE", entity.getEngagementId().toString());
        return toAssignmentResponse(entity);
    }

    @Transactional
    public AssignmentModels.Response updateAssignment(UUID publicId, AssignmentModels.UpdateRequest request) {
        AssignmentEntity entity = assignment(publicId);
        requireNotArchived(entity);
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        EngagementEntity engagement = engagement(entity.getEngagementId());
        OrgUnitEntity unit = structure.unit(entity.getOrgUnitId());
        PositionEntity position = structure.position(entity.getPositionId());
        validateAssignmentPeriod(period, engagement, unit, position);
        boolean identityChanged = !Objects.equals(entity.getPrimaryAssignment(), request.primary())
                || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setPrimaryAssignment(Boolean.TRUE.equals(request.primary()));
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.assignments(), entity, request.expectedVersion()), "ORG_ASSIGNMENT_DUPLICATE");
        audit(entity, "UPDATE", entity.getEngagementId().toString());
        return toAssignmentResponse(entity);
    }

    @Transactional
    public AssignmentModels.Response transitionAssignment(UUID publicId, LifecycleTransitionRequest request) {
        AssignmentEntity entity = assignment(publicId);
        LifecycleStatus current = LifecycleStatus.valueOf(entity.getStatus());
        if (current == LifecycleStatus.ACTIVE && request.targetStatus() == LifecycleStatus.INACTIVE) {
            throw new BizException("ORG_ASSIGNMENT_TERMINATION_REQUIRED", "Use the terminate operation to close an active assignment");
        }
        if (current == LifecycleStatus.INACTIVE && request.targetStatus() == LifecycleStatus.ACTIVE) {
            throw new BizException("ORG_ASSIGNMENT_REACTIVATION_FORBIDDEN", "Create a new assignment instead of reopening history");
        }
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            EngagementEntity engagement = engagement(entity.getEngagementId());
            OrgUnitEntity unit = structure.unit(entity.getOrgUnitId());
            PositionEntity position = structure.position(entity.getPositionId());
            validateAssignmentReferences(engagement, unit, position);
            requireActive(engagement, "Engagement");
            requireActive(unit, "Organization unit");
            requireActive(position, "Position");
            validateAssignmentPeriod(support.period(entity), engagement, unit, position);
            if (mappers.engagements().lockById(engagement.getId()) == null) {
                throw new BizException(ENGAGEMENT_NOT_FOUND, "The referenced engagement was not found");
            }
            requireNoOverlappingPrimary(entity);
        }
        try {
            if (support.transition(mappers.assignments(), entity, request.targetStatus(), request.expectedVersion())) {
                audit(entity, "LIFECYCLE", request.targetStatus().name());
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException("ORG_PRIMARY_ASSIGNMENT_CONFLICT", "The engagement already has a current primary assignment");
        }
        return toAssignmentResponse(entity);
    }

    @Transactional
    public AssignmentModels.Response terminateAssignment(UUID publicId, Instant effectiveAt, int expectedVersion) {
        AssignmentEntity entity = assignment(publicId);
        if (LifecycleStatus.valueOf(entity.getStatus()) != LifecycleStatus.ACTIVE) {
            throw new BizException("ORG_ASSIGNMENT_NOT_ACTIVE", "Only an active assignment can be terminated");
        }
        EffectivePeriod current = support.period(entity);
        if (effectiveAt == null || !effectiveAt.isAfter(current.validFrom())
                || current.validTo() != null && effectiveAt.isAfter(current.validTo())) {
            throw new BizException("ORG_TERMINATION_TIME_INVALID", "Termination must be after validFrom and not after validTo");
        }
        entity.setValidTo(java.time.LocalDateTime.ofInstant(effectiveAt, java.time.ZoneOffset.UTC));
        entity.setStatus(LifecycleStatus.INACTIVE.name());
        support.update(mappers.assignments(), entity, expectedVersion);
        audit(entity, "TERMINATE", effectiveAt.toString());
        return toAssignmentResponse(entity);
    }

    @Transactional
    public void deleteAssignment(UUID publicId, int expectedVersion) {
        AssignmentEntity entity = assignment(publicId);
        support.softDelete(mappers.assignments(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getEngagementId().toString());
    }

    PersonEntity person(UUID publicId) {
        return support.byPublicId(mappers.persons(), publicId, PERSON_NOT_FOUND);
    }

    PersonEntity person(Long id) {
        return support.byInternalId(mappers.persons(), id, PERSON_NOT_FOUND);
    }

    EngagementEntity engagement(UUID publicId) {
        return support.byPublicId(mappers.engagements(), publicId, ENGAGEMENT_NOT_FOUND);
    }

    EngagementEntity engagement(Long id) {
        return support.byInternalId(mappers.engagements(), id, ENGAGEMENT_NOT_FOUND);
    }

    AssignmentEntity assignment(UUID publicId) {
        return support.byPublicId(mappers.assignments(), publicId, ASSIGNMENT_NOT_FOUND);
    }

    private void validateAssignmentReferences(EngagementEntity engagement,
                                              OrgUnitEntity unit,
                                              PositionEntity position) {
        OrganizationRules.requireSameOrganization(engagement.getOrganizationId(), unit.getOrganizationId(), "Assignment unit");
        OrganizationRules.requireSameOrganization(engagement.getOrganizationId(), position.getOrganizationId(), "Assignment position");
        if (position.getOrgUnitId() != null && !Objects.equals(position.getOrgUnitId(), unit.getId())) {
            throw new BizException("ORG_ASSIGNMENT_POSITION_UNIT_MISMATCH", "The position is owned by a different organization unit");
        }
    }

    private void validateAssignmentPeriod(EffectivePeriod period,
                                          EngagementEntity engagement,
                                          OrgUnitEntity unit,
                                          PositionEntity position) {
        requireWithin(period, support.period(engagement), "Assignment");
        requireWithin(period, support.period(unit), "Assignment");
        requireWithin(period, support.period(position), "Assignment");
    }

    private void requireNoOverlappingPrimary(AssignmentEntity candidate) {
        if (!Boolean.TRUE.equals(candidate.getPrimaryAssignment())) {
            return;
        }
        EffectivePeriod candidatePeriod = support.period(candidate);
        List<AssignmentEntity> activePrimaryAssignments = mappers.assignments().selectList(
                new QueryWrapper<AssignmentEntity>()
                        .eq("engagement_id", candidate.getEngagementId())
                        .eq("is_primary", 1)
                        .eq("status", LifecycleStatus.ACTIVE.name())
                        .eq("deleted", 0)
                        .ne(candidate.getId() != null, "id", candidate.getId())
        );
        boolean overlaps = activePrimaryAssignments.stream()
                .map(support::period)
                .anyMatch(existing -> overlaps(candidatePeriod, existing));
        if (overlaps) {
            throw new BizException("ORG_PRIMARY_ASSIGNMENT_CONFLICT", "Primary assignment periods cannot overlap");
        }
    }

    private boolean overlaps(EffectivePeriod left, EffectivePeriod right) {
        boolean leftStartsBeforeRightEnds = right.validTo() == null || left.validFrom().isBefore(right.validTo());
        boolean rightStartsBeforeLeftEnds = left.validTo() == null || right.validFrom().isBefore(left.validTo());
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds;
    }

    private PersonModels.Response toPersonResponse(PersonEntity entity) {
        return new PersonModels.Response(
                support.toUuid(entity.getPublicId()),
                entity.getPersonCode(),
                entity.getDisplayName(),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private EngagementModels.Response toEngagementResponse(EngagementEntity entity) {
        PersonEntity person = person(entity.getPersonId());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        return new EngagementModels.Response(
                support.toUuid(entity.getPublicId()),
                entity.getEngagementCode(),
                support.toUuid(person.getPublicId()),
                support.toUuid(organization.getPublicId()),
                new TypeDescriptorInput(entity.getTypeNamespace(), entity.getTypeCode(), entity.getTypeVersion()),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private AssignmentModels.Response toAssignmentResponse(AssignmentEntity entity) {
        EngagementEntity engagement = engagement(entity.getEngagementId());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        OrgUnitEntity unit = structure.unit(entity.getOrgUnitId());
        PositionEntity position = structure.position(entity.getPositionId());
        return new AssignmentModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(engagement.getPublicId()),
                support.toUuid(organization.getPublicId()),
                support.toUuid(unit.getPublicId()),
                support.toUuid(position.getPublicId()),
                Boolean.TRUE.equals(entity.getPrimaryAssignment()),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private boolean typeChanged(EngagementEntity entity, TypeDescriptor type) {
        return !Objects.equals(entity.getTypeNamespace(), type.namespace())
                || !Objects.equals(entity.getTypeCode(), type.code())
                || !Objects.equals(entity.getTypeVersion(), type.version());
    }

    private long count(BaseMapper<?> mapper, String column, Long id, boolean activeOnly) {
        QueryWrapper<Object> query = new QueryWrapper<>().eq(column, id).eq("deleted", 0);
        if (activeOnly) {
            query.eq("status", LifecycleStatus.ACTIVE.name());
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        long count = ((BaseMapper) mapper).selectCount(query);
        return count;
    }

    private long countUnarchived(BaseMapper<?> mapper, String column, Long id) {
        QueryWrapper<Object> query = new QueryWrapper<>()
                .eq(column, id)
                .eq("deleted", 0)
                .ne("status", LifecycleStatus.ARCHIVED.name());
        @SuppressWarnings({"rawtypes", "unchecked"})
        long count = ((BaseMapper) mapper).selectCount(query);
        return count;
    }

    private void requireWithin(EffectivePeriod child, EffectivePeriod parent, String resource) {
        if (!child.isWithin(parent)) {
            throw new BizException("ORG_PERIOD_OUTSIDE_PARENT", resource + " period must be within its owning resource period");
        }
    }

    private void requireActive(AbstractOrganizationResourceEntity entity, String resource) {
        if (LifecycleStatus.valueOf(entity.getStatus()) != LifecycleStatus.ACTIVE) {
            throw new BizException("ORG_PARENT_NOT_ACTIVE", resource + " must be ACTIVE");
        }
    }

    private void requireNotArchived(AbstractOrganizationResourceEntity entity) {
        if (LifecycleStatus.valueOf(entity.getStatus()) == LifecycleStatus.ARCHIVED) {
            throw new BizException("ORG_ARCHIVED_IMMUTABLE", "Restore an archived resource before updating it");
        }
    }

    private void insert(Supplier<Integer> operation, String duplicateCode) {
        try {
            if (operation.get() != 1) {
                throw new BizException("ORG_PERSISTENCE_FAILED", "The organization resource was not created");
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException(duplicateCode, "The organization resource already exists");
        }
    }

    private void update(Runnable operation, String duplicateCode) {
        try {
            operation.run();
        } catch (DuplicateKeyException exception) {
            throw new BizException(duplicateCode, "The organization resource already exists");
        }
    }

    private void audit(AbstractOrganizationResourceEntity entity, String operation, String value) {
        auditTrail.record("organization", entity.getClass().getSimpleName(), entity.getPublicId(), operation, value, entity.getStatus(), true);
    }
}
