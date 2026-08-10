package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.organization.api.model.OrgUnitModels;
import com.saasbasics.platform.modules.organization.api.model.OrgUnitRelationModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.api.model.OrganizationRelationModels;
import com.saasbasics.platform.modules.organization.api.model.PositionModels;
import com.saasbasics.platform.modules.organization.domain.model.EffectivePeriod;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.domain.model.OrganizationRules;
import com.saasbasics.platform.modules.organization.domain.model.TypeDescriptor;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractOrganizationResourceEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.AbstractTypedResourceEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgUnitEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgUnitRelationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationRelationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PositionEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationStructureService {

    private static final String RELATION_NOT_FOUND = "ORG_RELATION_NOT_FOUND";
    private static final String UNIT_NOT_FOUND = "ORG_UNIT_NOT_FOUND";
    private static final String UNIT_RELATION_NOT_FOUND = "ORG_UNIT_RELATION_NOT_FOUND";
    private static final String POSITION_NOT_FOUND = "ORG_POSITION_NOT_FOUND";

    private final OrganizationMapperAccess mappers;
    private final OrganizationEntitySupport support;
    private final OrganizationCatalogService catalog;
    private final AuditRecorder auditTrail;

    public OrganizationStructureService(OrganizationMapperAccess mappers,
                                        OrganizationEntitySupport support,
                                        OrganizationCatalogService catalog,
                                        AuditRecorder auditTrail) {
        this.mappers = mappers;
        this.support = support;
        this.catalog = catalog;
        this.auditTrail = auditTrail;
    }

    public PageResponse<OrganizationRelationModels.Response> listOrganizationRelations(int page, int size) {
        Page<OrganizationRelationEntity> result = mappers.organizationRelations().selectPage(
                Page.of(page, size),
                new QueryWrapper<OrganizationRelationEntity>().eq("deleted", 0).orderByAsc("source_organization_id", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toOrganizationRelationResponse).toList(), result.getTotal());
    }

    public OrganizationRelationModels.Response getOrganizationRelation(UUID publicId) {
        return toOrganizationRelationResponse(organizationRelation(publicId));
    }

    @Transactional
    public OrganizationRelationModels.Response createOrganizationRelation(OrganizationRelationModels.CreateRequest request) {
        OrganizationEntity source = catalog.organization(request.sourceOrganizationPublicId());
        OrganizationEntity target = catalog.organization(request.targetOrganizationPublicId());
        OrganizationRules.requireDistinct(source.getId(), target.getId(), "Organization relation");
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(source), "Organization relation");
        requireWithin(period, support.period(target), "Organization relation");

        OrganizationRelationEntity entity = new OrganizationRelationEntity();
        entity.setSourceOrganizationId(source.getId());
        entity.setTargetOrganizationId(target.getId());
        support.applyType(entity, request.type().toDomain());
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.organizationRelations().insert(entity), "ORG_RELATION_DUPLICATE");
        audit(entity, "CREATE", entity.getTypeCode());
        return toOrganizationRelationResponse(entity);
    }

    @Transactional
    public OrganizationRelationModels.Response updateOrganizationRelation(UUID publicId,
                                                                            OrganizationRelationModels.UpdateRequest request) {
        OrganizationRelationEntity entity = organizationRelation(publicId);
        requireNotArchived(entity);
        TypeDescriptor type = request.type().toDomain();
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(catalog.organization(entity.getSourceOrganizationId())), "Organization relation");
        requireWithin(period, support.period(catalog.organization(entity.getTargetOrganizationId())), "Organization relation");
        boolean identityChanged = typeChanged(entity, type) || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        support.applyType(entity, type);
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.organizationRelations(), entity, request.expectedVersion()), "ORG_RELATION_DUPLICATE");
        audit(entity, "UPDATE", entity.getTypeCode());
        return toOrganizationRelationResponse(entity);
    }

    @Transactional
    public OrganizationRelationModels.Response transitionOrganizationRelation(UUID publicId,
                                                                                LifecycleTransitionRequest request) {
        OrganizationRelationEntity entity = organizationRelation(publicId);
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            OrganizationEntity source = catalog.organization(entity.getSourceOrganizationId());
            OrganizationEntity target = catalog.organization(entity.getTargetOrganizationId());
            requireActive(source, "Source organization");
            requireActive(target, "Target organization");
            requireWithin(support.period(entity), support.period(source), "Organization relation");
            requireWithin(support.period(entity), support.period(target), "Organization relation");
        }
        if (support.transition(mappers.organizationRelations(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toOrganizationRelationResponse(entity);
    }

    @Transactional
    public void deleteOrganizationRelation(UUID publicId, int expectedVersion) {
        OrganizationRelationEntity entity = organizationRelation(publicId);
        support.softDelete(mappers.organizationRelations(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getTypeCode());
    }

    public PageResponse<OrgUnitModels.Response> listUnits(int page, int size) {
        Page<OrgUnitEntity> result = mappers.units().selectPage(
                Page.of(page, size),
                new QueryWrapper<OrgUnitEntity>().eq("deleted", 0).orderByAsc("organization_id", "tree_path", "sort_order", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toUnitResponse).toList(), result.getTotal());
    }

    public OrgUnitModels.Response getUnit(UUID publicId) {
        return toUnitResponse(unit(publicId));
    }

    @Transactional
    public OrgUnitModels.Response createUnit(OrgUnitModels.CreateRequest request) {
        OrganizationEntity organization = catalog.organization(request.organizationPublicId());
        OrgUnitEntity parent = request.parentUnitPublicId() == null ? null : unit(request.parentUnitPublicId());
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(organization), "Organization unit");
        if (parent != null) {
            OrganizationRules.requireSameOrganization(organization.getId(), parent.getOrganizationId(), "Organization unit parent");
            requireWithin(period, support.period(parent), "Organization unit");
        }

        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setOrganizationId(organization.getId());
        entity.setParentUnitId(parent == null ? null : parent.getId());
        entity.setUnitCode(OrganizationRules.requireResourceCode(request.unitCode(), "unitCode"));
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        entity.setTreePath((parent == null ? "/" : parent.getTreePath()) + entity.getPublicId() + "/");
        insert(() -> mappers.units().insert(entity), "ORG_UNIT_CODE_DUPLICATE");
        audit(entity, "CREATE", entity.getUnitCode());
        return toUnitResponse(entity);
    }

    @Transactional
    public OrgUnitModels.Response updateUnit(UUID publicId, OrgUnitModels.UpdateRequest request) {
        OrgUnitEntity entity = unit(publicId);
        requireNotArchived(entity);
        String code = OrganizationRules.requireResourceCode(request.unitCode(), "unitCode");
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        requireWithin(period, support.period(organization), "Organization unit");
        OrgUnitEntity parent = entity.getParentUnitId() == null ? null : unit(entity.getParentUnitId());
        if (parent != null) {
            requireWithin(period, support.period(parent), "Organization unit");
        }
        boolean identityChanged = !Objects.equals(code, entity.getUnitCode()) || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setUnitCode(code);
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.units(), entity, request.expectedVersion()), "ORG_UNIT_CODE_DUPLICATE");
        audit(entity, "UPDATE", code);
        return toUnitResponse(entity);
    }

    @Transactional
    public OrgUnitModels.Response moveUnit(UUID publicId, OrgUnitModels.MoveRequest request) {
        OrgUnitEntity entity = unit(publicId);
        requireNotArchived(entity);
        OrgUnitEntity parent = request.parentUnitPublicId() == null ? null : unit(request.parentUnitPublicId());
        if (parent != null) {
            OrganizationRules.requireDistinct(entity.getId(), parent.getId(), "Organization unit parent");
            OrganizationRules.requireSameOrganization(entity.getOrganizationId(), parent.getOrganizationId(), "Organization unit parent");
            if (parent.getTreePath().startsWith(entity.getTreePath())) {
                throw new BizException("ORG_UNIT_CYCLE", "An organization unit cannot move below one of its descendants");
            }
            requireWithin(support.period(entity), support.period(parent), "Organization unit");
        }
        if (LifecycleStatus.valueOf(entity.getStatus()) == LifecycleStatus.ACTIVE) {
            requireActive(catalog.organization(entity.getOrganizationId()), "Owning organization");
            if (parent != null) {
                requireActive(parent, "Parent organization unit");
            }
        }
        String previousPath = entity.getTreePath();
        String nextPath = (parent == null ? "/" : parent.getTreePath()) + entity.getPublicId() + "/";
        entity.setParentUnitId(parent == null ? null : parent.getId());
        entity.setTreePath(nextPath);
        support.update(mappers.units(), entity, request.expectedVersion());
        rewriteDescendantPaths(previousPath, nextPath, entity.getId());
        audit(entity, "MOVE", nextPath);
        return toUnitResponse(entity);
    }

    @Transactional
    public OrgUnitModels.Response transitionUnit(UUID publicId, LifecycleTransitionRequest request) {
        OrgUnitEntity entity = unit(publicId);
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
            requireActive(organization, "Owning organization");
            requireWithin(support.period(entity), support.period(organization), "Organization unit");
            if (entity.getParentUnitId() != null) {
                OrgUnitEntity parent = unit(entity.getParentUnitId());
                requireActive(parent, "Parent organization unit");
                requireWithin(support.period(entity), support.period(parent), "Organization unit");
            }
        }
        if (request.targetStatus() == LifecycleStatus.INACTIVE) {
            requireNoActiveUnitDependents(entity.getId());
        }
        if (request.targetStatus() == LifecycleStatus.ARCHIVED) {
            requireNoUnarchivedUnitDependents(entity.getId());
        }
        if (support.transition(mappers.units(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toUnitResponse(entity);
    }

    @Transactional
    public void deleteUnit(UUID publicId, int expectedVersion) {
        OrgUnitEntity entity = unit(publicId);
        requireNoUnitReferences(entity.getId());
        support.softDelete(mappers.units(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getUnitCode());
    }

    public PageResponse<OrgUnitRelationModels.Response> listUnitRelations(int page, int size) {
        Page<OrgUnitRelationEntity> result = mappers.unitRelations().selectPage(
                Page.of(page, size),
                new QueryWrapper<OrgUnitRelationEntity>().eq("deleted", 0).orderByAsc("source_unit_id", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toUnitRelationResponse).toList(), result.getTotal());
    }

    public OrgUnitRelationModels.Response getUnitRelation(UUID publicId) {
        return toUnitRelationResponse(unitRelation(publicId));
    }

    @Transactional
    public OrgUnitRelationModels.Response createUnitRelation(OrgUnitRelationModels.CreateRequest request) {
        OrgUnitEntity source = unit(request.sourceUnitPublicId());
        OrgUnitEntity target = unit(request.targetUnitPublicId());
        OrganizationRules.requireDistinct(source.getId(), target.getId(), "Organization unit relation");
        OrganizationRules.requireSameOrganization(source.getOrganizationId(), target.getOrganizationId(), "Organization unit relation");
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(source), "Organization unit relation");
        requireWithin(period, support.period(target), "Organization unit relation");

        OrgUnitRelationEntity entity = new OrgUnitRelationEntity();
        entity.setSourceUnitId(source.getId());
        entity.setTargetUnitId(target.getId());
        support.applyType(entity, request.type().toDomain());
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.unitRelations().insert(entity), "ORG_UNIT_RELATION_DUPLICATE");
        audit(entity, "CREATE", entity.getTypeCode());
        return toUnitRelationResponse(entity);
    }

    @Transactional
    public OrgUnitRelationModels.Response updateUnitRelation(UUID publicId,
                                                              OrgUnitRelationModels.UpdateRequest request) {
        OrgUnitRelationEntity entity = unitRelation(publicId);
        requireNotArchived(entity);
        TypeDescriptor type = request.type().toDomain();
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(unit(entity.getSourceUnitId())), "Organization unit relation");
        requireWithin(period, support.period(unit(entity.getTargetUnitId())), "Organization unit relation");
        boolean identityChanged = typeChanged(entity, type) || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        support.applyType(entity, type);
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.unitRelations(), entity, request.expectedVersion()), "ORG_UNIT_RELATION_DUPLICATE");
        audit(entity, "UPDATE", entity.getTypeCode());
        return toUnitRelationResponse(entity);
    }

    @Transactional
    public OrgUnitRelationModels.Response transitionUnitRelation(UUID publicId,
                                                                  LifecycleTransitionRequest request) {
        OrgUnitRelationEntity entity = unitRelation(publicId);
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            OrgUnitEntity source = unit(entity.getSourceUnitId());
            OrgUnitEntity target = unit(entity.getTargetUnitId());
            requireActive(source, "Source organization unit");
            requireActive(target, "Target organization unit");
            requireWithin(support.period(entity), support.period(source), "Organization unit relation");
            requireWithin(support.period(entity), support.period(target), "Organization unit relation");
        }
        if (support.transition(mappers.unitRelations(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toUnitRelationResponse(entity);
    }

    @Transactional
    public void deleteUnitRelation(UUID publicId, int expectedVersion) {
        OrgUnitRelationEntity entity = unitRelation(publicId);
        support.softDelete(mappers.unitRelations(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getTypeCode());
    }

    public PageResponse<PositionModels.Response> listPositions(int page, int size) {
        Page<PositionEntity> result = mappers.positions().selectPage(
                Page.of(page, size),
                new QueryWrapper<PositionEntity>().eq("deleted", 0).orderByAsc("organization_id", "position_code", "id")
        );
        return new PageResponse<>(result.getRecords().stream().map(this::toPositionResponse).toList(), result.getTotal());
    }

    public PositionModels.Response getPosition(UUID publicId) {
        return toPositionResponse(position(publicId));
    }

    @Transactional
    public PositionModels.Response createPosition(PositionModels.CreateRequest request) {
        OrganizationEntity organization = catalog.organization(request.organizationPublicId());
        OrgUnitEntity unit = request.orgUnitPublicId() == null ? null : unit(request.orgUnitPublicId());
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        requireWithin(period, support.period(organization), "Position");
        if (unit != null) {
            OrganizationRules.requireSameOrganization(organization.getId(), unit.getOrganizationId(), "Position unit");
            requireWithin(period, support.period(unit), "Position");
        }

        PositionEntity entity = new PositionEntity();
        entity.setOrganizationId(organization.getId());
        entity.setOrgUnitId(unit == null ? null : unit.getId());
        entity.setPositionCode(OrganizationRules.requireResourceCode(request.positionCode(), "positionCode"));
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        support.initialize(entity, request.validFrom(), request.validTo(), request.remark());
        insert(() -> mappers.positions().insert(entity), "ORG_POSITION_CODE_DUPLICATE");
        audit(entity, "CREATE", entity.getPositionCode());
        return toPositionResponse(entity);
    }

    @Transactional
    public PositionModels.Response updatePosition(UUID publicId, PositionModels.UpdateRequest request) {
        PositionEntity entity = position(publicId);
        requireNotArchived(entity);
        OrgUnitEntity unit = request.orgUnitPublicId() == null ? null : unit(request.orgUnitPublicId());
        String code = OrganizationRules.requireResourceCode(request.positionCode(), "positionCode");
        EffectivePeriod period = new EffectivePeriod(request.validFrom(), request.validTo());
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        requireWithin(period, support.period(organization), "Position");
        if (unit != null) {
            OrganizationRules.requireSameOrganization(entity.getOrganizationId(), unit.getOrganizationId(), "Position unit");
            requireWithin(period, support.period(unit), "Position");
        }
        Long nextUnitId = unit == null ? null : unit.getId();
        boolean identityChanged = !Objects.equals(code, entity.getPositionCode())
                || !Objects.equals(nextUnitId, entity.getOrgUnitId())
                || !support.period(entity).equals(period);
        support.requireDraftForIdentityChange(entity, identityChanged);
        entity.setOrgUnitId(nextUnitId);
        entity.setPositionCode(code);
        entity.setDisplayName(OrganizationRules.requireDisplayName(request.displayName(), "displayName"));
        support.applyPeriod(entity, request.validFrom(), request.validTo());
        entity.setRemark(support.normalizeOptional(request.remark()));
        update(() -> support.update(mappers.positions(), entity, request.expectedVersion()), "ORG_POSITION_CODE_DUPLICATE");
        audit(entity, "UPDATE", code);
        return toPositionResponse(entity);
    }

    @Transactional
    public PositionModels.Response transitionPosition(UUID publicId, LifecycleTransitionRequest request) {
        PositionEntity entity = position(publicId);
        if (request.targetStatus() == LifecycleStatus.ACTIVE) {
            OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
            requireActive(organization, "Owning organization");
            requireWithin(support.period(entity), support.period(organization), "Position");
            if (entity.getOrgUnitId() != null) {
                OrgUnitEntity unit = unit(entity.getOrgUnitId());
                requireActive(unit, "Owning organization unit");
                requireWithin(support.period(entity), support.period(unit), "Position");
            }
        }
        if (request.targetStatus() == LifecycleStatus.INACTIVE) {
            requireNoActivePositionAssignments(entity.getId());
        }
        if (request.targetStatus() == LifecycleStatus.ARCHIVED
                && countUnarchived(mappers.assignments(), "position_id", entity.getId()) > 0) {
            throw new BizException("ORG_POSITION_UNARCHIVED_ASSIGNMENTS", "Assignments must be archived first");
        }
        if (support.transition(mappers.positions(), entity, request.targetStatus(), request.expectedVersion())) {
            audit(entity, "LIFECYCLE", request.targetStatus().name());
        }
        return toPositionResponse(entity);
    }

    @Transactional
    public void deletePosition(UUID publicId, int expectedVersion) {
        PositionEntity entity = position(publicId);
        if (count(mappers.assignments(), "position_id", entity.getId(), false) > 0) {
            throw new BizException("ORG_POSITION_REFERENCED", "Referenced positions cannot be deleted");
        }
        support.softDelete(mappers.positions(), entity, expectedVersion);
        audit(entity, "DELETE", entity.getPositionCode());
    }

    OrgUnitEntity unit(UUID publicId) {
        return support.byPublicId(mappers.units(), publicId, UNIT_NOT_FOUND);
    }

    OrgUnitEntity unit(Long id) {
        return support.byInternalId(mappers.units(), id, UNIT_NOT_FOUND);
    }

    PositionEntity position(UUID publicId) {
        return support.byPublicId(mappers.positions(), publicId, POSITION_NOT_FOUND);
    }

    PositionEntity position(Long id) {
        return support.byInternalId(mappers.positions(), id, POSITION_NOT_FOUND);
    }

    private OrganizationRelationEntity organizationRelation(UUID publicId) {
        return support.byPublicId(mappers.organizationRelations(), publicId, RELATION_NOT_FOUND);
    }

    private OrgUnitRelationEntity unitRelation(UUID publicId) {
        return support.byPublicId(mappers.unitRelations(), publicId, UNIT_RELATION_NOT_FOUND);
    }

    private void rewriteDescendantPaths(String previousPath, String nextPath, Long movedUnitId) {
        List<OrgUnitEntity> descendants = mappers.units().selectList(
                new QueryWrapper<OrgUnitEntity>()
                        .eq("deleted", 0)
                        .ne("id", movedUnitId)
                        .likeRight("tree_path", previousPath)
                        .orderByAsc("tree_path")
        );
        for (OrgUnitEntity descendant : descendants) {
            descendant.setTreePath(nextPath + descendant.getTreePath().substring(previousPath.length()));
            support.update(mappers.units(), descendant, descendant.getVersion());
        }
    }

    private OrganizationRelationModels.Response toOrganizationRelationResponse(OrganizationRelationEntity entity) {
        OrganizationEntity source = catalog.organization(entity.getSourceOrganizationId());
        OrganizationEntity target = catalog.organization(entity.getTargetOrganizationId());
        return new OrganizationRelationModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(source.getPublicId()),
                support.toUuid(target.getPublicId()),
                type(entity),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private OrgUnitModels.Response toUnitResponse(OrgUnitEntity entity) {
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        UUID parentPublicId = entity.getParentUnitId() == null ? null : support.toUuid(unit(entity.getParentUnitId()).getPublicId());
        return new OrgUnitModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(organization.getPublicId()),
                parentPublicId,
                entity.getUnitCode(),
                entity.getDisplayName(),
                entity.getTreePath(),
                entity.getSortOrder(),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private OrgUnitRelationModels.Response toUnitRelationResponse(OrgUnitRelationEntity entity) {
        OrgUnitEntity source = unit(entity.getSourceUnitId());
        OrgUnitEntity target = unit(entity.getTargetUnitId());
        return new OrgUnitRelationModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(source.getPublicId()),
                support.toUuid(target.getPublicId()),
                type(entity),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private PositionModels.Response toPositionResponse(PositionEntity entity) {
        OrganizationEntity organization = catalog.organization(entity.getOrganizationId());
        UUID unitPublicId = entity.getOrgUnitId() == null ? null : support.toUuid(unit(entity.getOrgUnitId()).getPublicId());
        return new PositionModels.Response(
                support.toUuid(entity.getPublicId()),
                support.toUuid(organization.getPublicId()),
                unitPublicId,
                entity.getPositionCode(),
                entity.getDisplayName(),
                LifecycleStatus.valueOf(entity.getStatus()),
                support.toInstant(entity.getValidFrom()),
                support.toInstant(entity.getValidTo()),
                entity.getRemark(),
                entity.getVersion()
        );
    }

    private TypeDescriptorInput type(AbstractTypedResourceEntity entity) {
        return new TypeDescriptorInput(entity.getTypeNamespace(), entity.getTypeCode(), entity.getTypeVersion());
    }

    private boolean typeChanged(AbstractTypedResourceEntity entity, TypeDescriptor type) {
        return !Objects.equals(entity.getTypeNamespace(), type.namespace())
                || !Objects.equals(entity.getTypeCode(), type.code())
                || !Objects.equals(entity.getTypeVersion(), type.version());
    }

    private void requireNoActiveUnitDependents(Long unitId) {
        long active = count(mappers.units(), "parent_unit_id", unitId, true)
                + count(mappers.positions(), "org_unit_id", unitId, true)
                + count(mappers.assignments(), "org_unit_id", unitId, true)
                + count(mappers.unitRelations(), "source_unit_id", unitId, true)
                + count(mappers.unitRelations(), "target_unit_id", unitId, true);
        if (active > 0) {
            throw new BizException("ORG_UNIT_ACTIVE_DEPENDENTS", "Active unit dependents must be deactivated first");
        }
    }

    private void requireNoUnitReferences(Long unitId) {
        long references = count(mappers.units(), "parent_unit_id", unitId, false)
                + count(mappers.positions(), "org_unit_id", unitId, false)
                + count(mappers.assignments(), "org_unit_id", unitId, false)
                + count(mappers.unitRelations(), "source_unit_id", unitId, false)
                + count(mappers.unitRelations(), "target_unit_id", unitId, false);
        if (references > 0) {
            throw new BizException("ORG_UNIT_REFERENCED", "Referenced organization units cannot be deleted");
        }
    }

    private void requireNoUnarchivedUnitDependents(Long unitId) {
        long dependents = countUnarchived(mappers.units(), "parent_unit_id", unitId)
                + countUnarchived(mappers.positions(), "org_unit_id", unitId)
                + countUnarchived(mappers.assignments(), "org_unit_id", unitId)
                + countUnarchived(mappers.unitRelations(), "source_unit_id", unitId)
                + countUnarchived(mappers.unitRelations(), "target_unit_id", unitId);
        if (dependents > 0) {
            throw new BizException("ORG_UNIT_UNARCHIVED_DEPENDENTS", "Unit dependents must be archived first");
        }
    }

    private void requireNoActivePositionAssignments(Long positionId) {
        if (count(mappers.assignments(), "position_id", positionId, true) > 0) {
            throw new BizException("ORG_POSITION_ACTIVE_ASSIGNMENTS", "Active assignments must be terminated first");
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
