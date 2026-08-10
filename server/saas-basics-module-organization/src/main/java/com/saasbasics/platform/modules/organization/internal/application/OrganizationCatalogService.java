package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.api.model.OrganizationModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationTypeAssignmentModels;
import com.saasbasics.platform.modules.organization.domain.model.EffectivePeriod;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.domain.model.OrganizationRules;
import com.saasbasics.platform.modules.organization.domain.model.TypeDescriptor;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractTypedResourceEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationCapabilityEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationClassificationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationEntity;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationCatalogService {

    private static final String ORGANIZATION_NOT_FOUND = "ORG_ORGANIZATION_NOT_FOUND";
    private static final String CLASSIFICATION_NOT_FOUND = "ORG_CLASSIFICATION_NOT_FOUND";
    private static final String CAPABILITY_NOT_FOUND = "ORG_CAPABILITY_NOT_FOUND";

    private final OrganizationMapperAccess mappers;
    private final OrganizationEntitySupport support;
    private final AuditRecorder auditTrail;

    public OrganizationCatalogService(OrganizationMapperAccess mappers,
                                      OrganizationEntitySupport support,
                                      AuditRecorder auditTrail) {
        this.mappers = mappers;
        this.support = support;
        this.auditTrail = auditTrail;
    }

    public PageResponse<OrganizationModels.Response> listOrganizations(int page, int size) {
        Page<OrganizationEntity> result = mappers.organizations().selectPage(
                Page.of(page, size),
                new QueryWrapper<OrganizationEntity>()
                        .eq("deleted", 0)
                        .orderByAsc("organization_code", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toOrganizationResponse).toList(), result.getTotal());
    }

    public OrganizationModels.Response getOrganization(UUID publicId) {
        return toOrganizationResponse(organization(publicId));
    }

    @Transactional
    public OrganizationModels.Response createOrganization(OrganizationModels.CreateRequest request) {
        OrganizationEntity entity = new OrganizationEntity();
        entity.setOrganizationCode(OrganizationRules.requireResourceCode(request.organizationCode(), "organizationCode"));
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        entity.setDescription(support.normalizeOptional(request.description()));
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.organizations().insert(entity), "ORG_ORGANIZATION_CODE_DUPLICATE");
        audit(entity, "CREATE", entity.getOrganizationCode());
        return toOrganizationResponse(entity);
    }

    @Transactional
    public OrganizationModels.Response updateOrganization(UUID publicId, OrganizationModels.UpdateRequest request) {
        OrganizationEntity entity = organization(publicId);
        requireNotArchived(entity);
        String code = OrganizationRules.requireResourceCode(request.organizationCode(), "organizationCode");
        boolean identityChanged = !Objects.equals(entity.getOrganizationCode(), code)
                || !support.period(entity).equals(new EffectivePeriod(request.validFrom(), request.validTo()));
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setOrganizationCode(code);
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        entity.setDescription(support.normalizeOptional(request.description()));
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.organizations(), entity, request.expectedVersion()),
                "ORG_ORGANIZATION_CODE_DUPLICATE");
        audit(entity, "UPDATE", code);
        return toOrganizationResponse(entity);
    }

    @Transactional
    public OrganizationModels.Response transitionOrganization(UUID publicId, LifecycleTransitionRequest request) {
        OrganizationEntity entity = organization(publicId);
        if (request.targetStatus() == LifecycleStatus.INACTIVE) {
            requireNoActiveOrganizationDependents(entity.getId());
        }
        if (request.targetStatus() == LifecycleStatus.ARCHIVED) {
            requireNoUnarchivedOrganizationDependents(entity.getId());
        }
        if (support.transition(mappers.organizations(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toOrganizationResponse(entity);
    }

    @Transactional
    public void deleteOrganization(UUID publicId, int expectedVersion) {
        OrganizationEntity entity = organization(publicId);
        requireNoOrganizationReferences(entity.getId());
        support.softDelete(mappers.organizations(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getOrganizationCode());
    }

    public PageResponse<OrganizationTypeAssignmentModels.Response> listClassifications(int page, int size) {
        return listTyped(mappers.classifications(), page, size);
    }

    public OrganizationTypeAssignmentModels.Response getClassification(UUID publicId) {
        return toTypeAssignmentResponse(support.byPublicId(mappers.classifications(), publicId, CLASSIFICATION_NOT_FOUND));
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response createClassification(OrganizationTypeAssignmentModels.CreateRequest request) {
        return createTyped(
                request,
                new OrganizationClassificationEntity(),
                mappers.classifications(),
                "classification",
                "ORG_CLASSIFICATION_DUPLICATE"
        );
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response updateClassification(UUID publicId,
                                                                           OrganizationTypeAssignmentModels.UpdateRequest request) {
        return updateTyped(
                support.byPublicId(mappers.classifications(), publicId, CLASSIFICATION_NOT_FOUND),
                request,
                mappers.classifications(),
                "classification",
                "ORG_CLASSIFICATION_DUPLICATE"
        );
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response transitionClassification(UUID publicId,
                                                                                LifecycleTransitionRequest request) {
        return transitionTyped(
                support.byPublicId(mappers.classifications(), publicId, CLASSIFICATION_NOT_FOUND),
                mappers.classifications(),
                request,
                "classification"
        );
    }

    @Transactional
    public void deleteClassification(UUID publicId, int expectedVersion) {
        deleteTyped(
                support.byPublicId(mappers.classifications(), publicId, CLASSIFICATION_NOT_FOUND),
                mappers.classifications(),
                expectedVersion,
                "classification"
        );
    }

    public PageResponse<OrganizationTypeAssignmentModels.Response> listCapabilities(int page, int size) {
        return listTyped(mappers.capabilities(), page, size);
    }

    public OrganizationTypeAssignmentModels.Response getCapability(UUID publicId) {
        return toTypeAssignmentResponse(support.byPublicId(mappers.capabilities(), publicId, CAPABILITY_NOT_FOUND));
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response createCapability(OrganizationTypeAssignmentModels.CreateRequest request) {
        return createTyped(
                request,
                new OrganizationCapabilityEntity(),
                mappers.capabilities(),
                "capability",
                "ORG_CAPABILITY_DUPLICATE"
        );
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response updateCapability(UUID publicId,
                                                                       OrganizationTypeAssignmentModels.UpdateRequest request) {
        return updateTyped(
                support.byPublicId(mappers.capabilities(), publicId, CAPABILITY_NOT_FOUND),
                request,
                mappers.capabilities(),
                "capability",
                "ORG_CAPABILITY_DUPLICATE"
        );
    }

    @Transactional
    public OrganizationTypeAssignmentModels.Response transitionCapability(UUID publicId,
                                                                            LifecycleTransitionRequest request) {
        return transitionTyped(
                support.byPublicId(mappers.capabilities(), publicId, CAPABILITY_NOT_FOUND),
                mappers.capabilities(),
                request,
                "capability"
        );
    }

    @Transactional
    public void deleteCapability(UUID publicId, int expectedVersion) {
        deleteTyped(
                support.byPublicId(mappers.capabilities(), publicId, CAPABILITY_NOT_FOUND),
                mappers.capabilities(),
                expectedVersion,
                "capability"
        );
    }

    OrganizationEntity organization(UUID publicId) {
        return support.byPublicId(mappers.organizations(), publicId, ORGANIZATION_NOT_FOUND);
    }

    OrganizationEntity organization(Long id) {
        return support.byInternalId(mappers.organizations(), id, ORGANIZATION_NOT_FOUND);
    }

    private <E extends AbstractTypedResourceEntity> PageResponse<OrganizationTypeAssignmentModels.Response> listTyped(
            BaseMapper<E> mapper,
            int page,
            int size) {
        Page<E> result = mapper.selectPage(
                Page.of(page, size),
                new QueryWrapper<E>().eq("deleted", 0).orderByAsc("organization_id", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toTypeAssignmentResponse).toList(), result.getTotal());
    }

    private <E extends AbstractTypedResourceEntity> OrganizationTypeAssignmentModels.Response createTyped(
            OrganizationTypeAssignmentModels.CreateRequest request,
            E entity,
            BaseMapper<E> mapper,
            String resourceType,
            String duplicateCode) {
        OrganizationEntity organization = organization(request.organizationPublicId());
        TypeDescriptor type = request.type().toDomain();
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(organization), resourceType);
        setOrganizationId(entity, organization.getId());
        support.applyType(entity, type);
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mapper.insert(entity), duplicateCode);
        audit(entity, "CREATE", resourceType);
        return toTypeAssignmentResponse(entity);
    }

    private <E extends AbstractTypedResourceEntity> OrganizationTypeAssignmentModels.Response updateTyped(
            E entity,
            OrganizationTypeAssignmentModels.UpdateRequest request,
            BaseMapper<E> mapper,
            String resourceType,
            String duplicateCode) {
        requireNotArchived(entity);
        TypeDescriptor type = request.type().toDomain();
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        OrganizationEntity organization = organization(organizationId(entity));
        requireWithin(period, support.period(organization), resourceType);
        boolean identityChanged = !Objects.equals(entity.getTypeNamespace(), type.namespace())
                || !Objects.equals(entity.getTypeCode(), type.code())
                || !Objects.equals(entity.getTypeVersion(), type.version())
                || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        support.applyType(entity, type);
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mapper, entity, request.expectedVersion()), duplicateCode);
        audit(entity, "UPDATE", resourceType);
        return toTypeAssignmentResponse(entity);
    }

    private <E extends AbstractTypedResourceEntity> OrganizationTypeAssignmentModels.Response transitionTyped(
            E entity,
            BaseMapper<E> mapper,
            LifecycleTransitionRequest request,
            String resourceType) {
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            OrganizationEntity organization = organization(organizationId(entity));
            requireActive(organization, "The owning organization must be ACTIVE");
            requireWithin(support.period(entity), support.period(organization), resourceType);
        }
        if (support.transition(mapper, entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toTypeAssignmentResponse(entity);
    }

    private <E extends AbstractTypedResourceEntity> void deleteTyped(E entity,
                                                                      BaseMapper<E> mapper,
                                                                      int expectedVersion,
                                                                      String resourceType) {
        support.softDelete(mapper, entity, expectedVersion);
        audit(entity, "DELETE", resourceType);
    }

    private OrganizationModels.Response toOrganizationResponse(OrganizationEntity entity) {
        return new OrganizationModels.Response(
                support.toUuid(entity.getPublicId()),
                entity.getOrganizationCode(),
                entity.getDisplayName(),
                entity.getDescription(),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private OrganizationTypeAssignmentModels.Response toTypeAssignmentResponse(AbstractTypedResourceEntity entity) {
        OrganizationEntity organization = organization(organizationId(entity));
        return new OrganizationTypeAssignmentModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(organization.getPublicId()),
                new TypeDescriptorInput(entity.getTypeNamespace(), entity.getTypeCode(), entity.getTypeVersion()),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private Long organizationId(AbstractTypedResourceEntity entity) {
        if (entity instanceof OrganizationClassificationEntity classification) {
            return classification.getOrganizationId();
        }
        if (entity instanceof OrganizationCapabilityEntity capability) {
            return capability.getOrganizationId();
        }
        throw new IllegalArgumentException("Unsupported organization type assignment " + entity.getClass().getName());
    }

    private void setOrganizationId(AbstractTypedResourceEntity entity, Long organizationId) {
        if (entity instanceof OrganizationClassificationEntity classification) {
            classification.setOrganizationId(organizationId);
            return;
        }
        if (entity instanceof OrganizationCapabilityEntity capability) {
            capability.setOrganizationId(organizationId);
            return;
        }
        throw new IllegalArgumentException("Unsupported organization type assignment " + entity.getClass().getName());
    }

    private void requireNoActiveOrganizationDependents(Long organizationId) {
        long active = count(mappers.classifications(), "organization_id", organizationId, true)
                + count(mappers.capabilities(), "organization_id", organizationId, true)
                + count(mappers.organizationRelations(), "source_organization_id", organizationId, true)
                + count(mappers.organizationRelations(), "target_organization_id", organizationId, true)
                + count(mappers.units(), "organization_id", organizationId, true)
                + count(mappers.engagements(), "organization_id", organizationId, true)
                + count(mappers.positions(), "organization_id", organizationId, true)
                + count(mappers.assignments(), "organization_id", organizationId, true);
        if (active > 0) {
            throw new BizException("ORG_ACTIVE_DEPENDENTS", "Active dependent resources must be deactivated first");
        }
    }

    private void requireNoOrganizationReferences(Long organizationId) {
        long references = count(mappers.classifications(), "organization_id", organizationId, false)
                + count(mappers.capabilities(), "organization_id", organizationId, false)
                + count(mappers.organizationRelations(), "source_organization_id", organizationId, false)
                + count(mappers.organizationRelations(), "target_organization_id", organizationId, false)
                + count(mappers.units(), "organization_id", organizationId, false)
                + count(mappers.engagements(), "organization_id", organizationId, false)
                + count(mappers.positions(), "organization_id", organizationId, false)
                + count(mappers.assignments(), "organization_id", organizationId, false);
        if (references > 0) {
            throw new BizException("ORG_REFERENCES_EXIST", "Referenced organizations cannot be deleted");
        }
    }

    private void requireNoUnarchivedOrganizationDependents(Long organizationId) {
        long dependents = countUnarchived(mappers.classifications(), "organization_id", organizationId)
                + countUnarchived(mappers.capabilities(), "organization_id", organizationId)
                + countUnarchived(mappers.organizationRelations(), "source_organization_id", organizationId)
                + countUnarchived(mappers.organizationRelations(), "target_organization_id", organizationId)
                + countUnarchived(mappers.units(), "organization_id", organizationId)
                + countUnarchived(mappers.engagements(), "organization_id", organizationId)
                + countUnarchived(mappers.positions(), "organization_id", organizationId)
                + countUnarchived(mappers.assignments(), "organization_id", organizationId);
        if (dependents > 0) {
            throw new BizException("ORG_UNARCHIVED_DEPENDENTS", "Dependent resources must be archived first");
        }
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

    private void requireWithin(EffectivePeriod child, EffectivePeriod parent, String resourceType) {
        if (!child.isWithin(parent)) {
            throw new BizException("ORG_PERIOD_OUTSIDE_PARENT", resourceType + " period must be within its owning resource period");
        }
    }

    private void requireActive(OrganizationEntity organization, String message) {
        if (LifecycleStatus.valueOf(organization.getStatus()) != LifecycleStatus.ACTIVE) {
            throw new BizException("ORG_PARENT_NOT_ACTIVE", message);
        }
    }

    private void requireNotArchived(com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity entity) {
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

    private void audit(com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity entity,
                       String operation,
                       String value) {
        auditTrail.record("organization", entity.getClass().getSimpleName(), entity.getPublicId(), operation, value, entity.getStatus(), true);
    }
}
