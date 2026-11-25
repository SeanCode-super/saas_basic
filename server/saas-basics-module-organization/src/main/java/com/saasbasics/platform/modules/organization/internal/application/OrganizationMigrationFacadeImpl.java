package com.saasbasics.platform.modules.organization.internal.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.modules.audit.api.AuditRecorder;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade;
import com.saasbasics.platform.modules.organization.api.model.AssignmentModels;
import com.saasbasics.platform.modules.organization.api.model.EngagementModels;
import com.saasbasics.platform.modules.organization.api.model.OrgUnitModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.api.model.OrganizationModels;
import com.saasbasics.platform.modules.organization.api.model.PersonModels;
import com.saasbasics.platform.modules.organization.api.model.PositionModels;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgMigrationIssueEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgMigrationResourceMapEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgMigrationRunEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgMigrationScopeEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgUnitEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrganizationEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PersonEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.PositionEntity;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgMigrationIssueMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgMigrationResourceMapMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgMigrationRunMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.mapper.OrgMigrationScopeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repeatable, idempotent V26-backed migration engine from legacy IAM department/position/employee
 * tables into the standard Organization / OrgUnit / Position / Person / Engagement / Assignment core.
 */
@Service
public class OrganizationMigrationFacadeImpl implements OrganizationMigrationFacade {

    private static final String SOURCE_NAMESPACE = "urn:saas-basics:legacy:iam";
    private static final String ENGAGEMENT_TYPE_NS = "urn:saas-basics:organization:engagement-type";
    private static final Instant BASE_EFFECTIVE_FROM = Instant.parse("2020-01-01T00:00:00Z");

    private final OrganizationMapperAccess mappers;
    private final OrganizationCatalogService catalog;
    private final OrganizationStructureService structure;
    private final OrganizationWorkforceService workforce;
    private final ObjectProvider<OrgMigrationScopeMapper> scopeMapperProvider;
    private final ObjectProvider<OrgMigrationRunMapper> runMapperProvider;
    private final ObjectProvider<OrgMigrationResourceMapMapper> mapMapperProvider;
    private final ObjectProvider<OrgMigrationIssueMapper> issueMapperProvider;
    private final AuditRecorder auditRecorder;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    public OrganizationMigrationFacadeImpl(
            OrganizationMapperAccess mappers,
            OrganizationCatalogService catalog,
            OrganizationStructureService structure,
            OrganizationWorkforceService workforce,
            ObjectProvider<OrgMigrationScopeMapper> scopeMapperProvider,
            ObjectProvider<OrgMigrationRunMapper> runMapperProvider,
            ObjectProvider<OrgMigrationResourceMapMapper> mapMapperProvider,
            ObjectProvider<OrgMigrationIssueMapper> issueMapperProvider,
            AuditRecorder auditRecorder
    ) {
        this.mappers = mappers;
        this.catalog = catalog;
        this.structure = structure;
        this.workforce = workforce;
        this.scopeMapperProvider = scopeMapperProvider;
        this.runMapperProvider = runMapperProvider;
        this.mapMapperProvider = mapMapperProvider;
        this.issueMapperProvider = issueMapperProvider;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @Transactional
    public MigrationReport execute(LegacyOrganizationSnapshot snapshot, MigrationMode mode) {
        if (snapshot == null) {
            throw new BizException("ORG_MIGRATION_SNAPSHOT_REQUIRED", "Legacy organization snapshot is required");
        }
        MigrationMode effectiveMode = mode == null ? MigrationMode.VALIDATE : mode;
        OrgMigrationScopeMapper scopeMapper = require(scopeMapperProvider);
        OrgMigrationRunMapper runMapper = require(runMapperProvider);
        OrgMigrationResourceMapMapper mapMapper = require(mapMapperProvider);
        OrgMigrationIssueMapper issueMapper = require(issueMapperProvider);

        List<LegacyDepartmentInput> departments = snapshot.departments() == null ? List.of() : snapshot.departments();
        List<LegacyPositionInput> positions = snapshot.positions() == null ? List.of() : snapshot.positions();
        List<LegacyEmployeeInput> employees = snapshot.employees() == null ? List.of() : snapshot.employees();

        int sourceCount = departments.size() + positions.size() + employees.size();
        OrganizationEntity organization = resolveOrCreateRootOrganization(snapshot, effectiveMode == MigrationMode.APPLY);
        Long targetOrgId = organization != null ? organization.getId() : 0L;

        OrgMigrationScopeEntity scope = resolveOrCreateScope(scopeMapper, snapshot, targetOrgId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        OrgMigrationRunEntity run = new OrgMigrationRunEntity();
        run.setTenantId(snapshot.tenantId());
        run.setPublicId(uuidGenerator.generate().toString());
        run.setMigrationScopeId(scope.getId());
        run.setMigrationRevision("1.0.0");
        run.setRunMode(effectiveMode.name());
        run.setSnapshotAt(now);
        run.setSourceFingerprint(sha256(snapshot.tenantCode() + "|" + sourceCount));
        run.setSourceResourceCount((long) sourceCount);
        run.setMappedResourceCount(0L);
        run.setBlockingIssueCount(0L);
        run.setWarningIssueCount(0L);
        run.setStartedAt(now);
        run.setStatus(effectiveMode == MigrationMode.VALIDATE ? "VALIDATING"
                : effectiveMode == MigrationMode.APPLY ? "BACKFILLING" : "VERIFYING");
        run.setVersion(0);
        run.setDeleted(0);
        runMapper.insert(run);

        List<IssueSummary> issues = new ArrayList<>();
        validateInputs(departments, positions, employees, issues);

        Map<Long, UUID> employeeToPerson = new LinkedHashMap<>();
        int mappedCount = 0;

        long blockingCount = issues.stream().filter(i -> "BLOCKING".equals(i.severity())).count();
        if (effectiveMode == MigrationMode.APPLY && blockingCount == 0) {
            if (organization != null) {
                mappedCount = applySnapshot(snapshot, organization, scope, run, departments, positions, employees,
                        mapMapper, employeeToPerson);
                scope.setStatus("CUTOVER_READY");
                scopeMapper.updateById(scope);
            }
        } else if (effectiveMode == MigrationMode.VERIFY) {
            mappedCount = verifySnapshot(snapshot, targetOrgId, scope, run, departments, positions, employees,
                    mapMapper, employeeToPerson, issues);
            if (issues.stream().noneMatch(i -> "BLOCKING".equals(i.severity()))) {
                scope.setStatus("CUTOVER");
                scopeMapper.updateById(scope);
            }
        } else {
            // VALIDATE: populate existing mappings if already applied
            for (OrgMigrationResourceMapEntity map : loadScopeMaps(mapMapper, scope.getId())) {
                if ("EMPLOYEE".equals(map.getSourceResourceType()) && map.getTargetPublicId() != null) {
                    try {
                        employeeToPerson.put(Long.parseLong(map.getSourceResourceKey()), UUID.fromString(map.getTargetPublicId()));
                    } catch (Exception ignored) {
                    }
                }
            }
            mappedCount = employeeToPerson.size();
        }

        blockingCount = issues.stream().filter(i -> "BLOCKING".equals(i.severity())).count();
        long warningCount = issues.stream().filter(i -> "WARNING".equals(i.severity())).count();

        for (IssueSummary issue : issues) {
            OrgMigrationIssueEntity row = new OrgMigrationIssueEntity();
            row.setTenantId(snapshot.tenantId());
            row.setPublicId(uuidGenerator.generate().toString());
            row.setMigrationScopeId(scope.getId());
            row.setMigrationRunId(run.getId());
            row.setSourceResourceType(issue.resourceType());
            row.setSourceResourceKey(issue.resourceKey());
            row.setIssueCode(issue.issueCode());
            row.setSeverity(issue.severity());
            row.setFieldName("");
            row.setDetail(issue.detail());
            row.setStatus("OPEN");
            row.setVersion(0);
            row.setDeleted(0);
            issueMapper.insert(row);
        }

        String finalStatus = blockingCount > 0 ? "BLOCKED" : "SUCCEEDED";
        run.setMappedResourceCount((long) mappedCount);
        run.setBlockingIssueCount(blockingCount);
        run.setWarningIssueCount(warningCount);
        run.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        run.setStatus(finalStatus);
        runMapper.updateById(run);

        auditRecorder.record("organization", "org_migration_run", run.getPublicId(),
                effectiveMode.name(), null, finalStatus, blockingCount == 0);

        return new MigrationReport(
                UUID.fromString(run.getPublicId()),
                UUID.fromString(scope.getPublicId()),
                effectiveMode,
                finalStatus,
                sourceCount,
                mappedCount,
                (int) blockingCount,
                (int) warningCount,
                organization != null ? UUID.fromString(organization.getPublicId()) : null,
                Map.copyOf(employeeToPerson),
                List.copyOf(issues)
        );
    }

    private OrganizationEntity resolveOrCreateRootOrganization(LegacyOrganizationSnapshot snapshot, boolean createIfMissing) {
        String orgCode = normalizeCode(snapshot.organizationCode(), "org-" + snapshot.tenantId());
        OrganizationEntity existing = mappers.organizations().selectOne(new QueryWrapper<OrganizationEntity>()
                .eq("organization_code", orgCode)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (existing != null) {
            if (createIfMissing && LifecycleStatus.DRAFT.name().equals(existing.getStatus())) {
                catalog.transitionOrganization(UUID.fromString(existing.getPublicId()),
                        new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, existing.getVersion()));
                existing = mappers.organizations().selectById(existing.getId());
            }
            return existing;
        }
        if (!createIfMissing) {
            return null;
        }
        String displayName = (snapshot.organizationName() == null || snapshot.organizationName().isBlank())
                ? orgCode : snapshot.organizationName().trim();
        OrganizationModels.Response created = catalog.createOrganization(new OrganizationModels.CreateRequest(
                orgCode, displayName, "Migrated legacy tenant root organization", BASE_EFFECTIVE_FROM, null, "legacy-migration"
        ));
        catalog.transitionOrganization(created.publicId(),
                new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, created.version()));
        return mappers.organizations().selectOne(new QueryWrapper<OrganizationEntity>()
                .eq("public_id", created.publicId().toString())
                .eq("deleted", 0)
                .last("LIMIT 1"));
    }

    private OrgMigrationScopeEntity resolveOrCreateScope(
            OrgMigrationScopeMapper scopeMapper,
            LegacyOrganizationSnapshot snapshot,
            Long targetOrgId
    ) {
        String scopeKey = "tenant-" + snapshot.tenantId();
        OrgMigrationScopeEntity existing = scopeMapper.selectOne(new QueryWrapper<OrgMigrationScopeEntity>()
                .eq("source_namespace", SOURCE_NAMESPACE)
                .eq("source_scope_type", "TENANT_DIRECTORY")
                .eq("source_scope_key", scopeKey)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (existing != null) {
            if ((existing.getTargetOrganizationId() == null || existing.getTargetOrganizationId() == 0L)
                    && targetOrgId != null && targetOrgId > 0L) {
                existing.setTargetOrganizationId(targetOrgId);
                scopeMapper.updateById(existing);
            }
            return existing;
        }
        OrgMigrationScopeEntity scope = new OrgMigrationScopeEntity();
        scope.setTenantId(snapshot.tenantId());
        scope.setPublicId(uuidGenerator.generate().toString());
        scope.setSourceNamespace(SOURCE_NAMESPACE);
        scope.setSourceScopeType("TENANT_DIRECTORY");
        scope.setSourceScopeKey(scopeKey);
        scope.setTargetOrganizationId(targetOrgId);
        scope.setEffectiveAt(LocalDateTime.ofInstant(BASE_EFFECTIVE_FROM, ZoneOffset.UTC));
        scope.setConfigurationFingerprint(sha256(SOURCE_NAMESPACE + ":" + scopeKey));
        scope.setStatus("READY");
        scope.setVersion(0);
        scope.setDeleted(0);
        scopeMapper.insert(scope);
        return scope;
    }

    private void validateInputs(
            List<LegacyDepartmentInput> departments,
            List<LegacyPositionInput> positions,
            List<LegacyEmployeeInput> employees,
            List<IssueSummary> issues
    ) {
        Set<Long> deptIds = new HashSet<>();
        for (LegacyDepartmentInput d : departments) {
            deptIds.add(d.legacyId());
            if (d.name() == null || d.name().isBlank()) {
                issues.add(new IssueSummary("DEPARTMENT", String.valueOf(d.legacyId()),
                        "MISSING_NAME", "BLOCKING", "Department name is blank"));
            }
        }
        for (LegacyDepartmentInput d : departments) {
            if (d.parentLegacyId() != null && d.parentLegacyId() > 0 && !deptIds.contains(d.parentLegacyId())) {
                issues.add(new IssueSummary("DEPARTMENT", String.valueOf(d.legacyId()),
                        "ORPHAN_PARENT_DEPARTMENT", "WARNING",
                        "Parent department " + d.parentLegacyId() + " not found; unit will be mounted at root"));
            }
        }
        Set<Long> posIds = new HashSet<>();
        for (LegacyPositionInput p : positions) {
            posIds.add(p.legacyId());
            if (p.name() == null || p.name().isBlank()) {
                issues.add(new IssueSummary("POSITION", String.valueOf(p.legacyId()),
                        "MISSING_NAME", "BLOCKING", "Position name is blank"));
            }
        }
        for (LegacyEmployeeInput e : employees) {
            if (e.employeeName() == null || e.employeeName().isBlank()) {
                issues.add(new IssueSummary("EMPLOYEE", String.valueOf(e.legacyId()),
                        "MISSING_NAME", "BLOCKING", "Employee name is blank"));
            }
            if (e.legacyDepartmentId() != null && e.legacyDepartmentId() > 0 && !deptIds.contains(e.legacyDepartmentId())) {
                issues.add(new IssueSummary("EMPLOYEE", String.valueOf(e.legacyId()),
                        "MISSING_DEPARTMENT", "BLOCKING",
                        "Employee references missing department " + e.legacyDepartmentId()));
            }
            if (e.legacyPositionId() != null && e.legacyPositionId() > 0 && !posIds.contains(e.legacyPositionId())) {
                issues.add(new IssueSummary("EMPLOYEE", String.valueOf(e.legacyId()),
                        "MISSING_POSITION", "WARNING",
                        "Employee references missing position " + e.legacyPositionId() + "; fallback staff position will be used"));
            }
        }
    }

    private int applySnapshot(
            LegacyOrganizationSnapshot snapshot,
            OrganizationEntity organization,
            OrgMigrationScopeEntity scope,
            OrgMigrationRunEntity run,
            List<LegacyDepartmentInput> departments,
            List<LegacyPositionInput> positions,
            List<LegacyEmployeeInput> employees,
            OrgMigrationResourceMapMapper mapMapper,
            Map<Long, UUID> employeeToPerson
    ) {
        UUID orgUuid = UUID.fromString(organization.getPublicId());
        Map<String, OrgMigrationResourceMapEntity> existingMaps = indexMaps(loadScopeMaps(mapMapper, scope.getId()));
        int mappedCount = 0;

        // 1. Migrate departments topologically into OrgUnits
        Map<Long, UUID> deptToUnitUuid = new HashMap<>();
        List<LegacyDepartmentInput> orderedDepts = topoSortDepartments(departments);
        for (LegacyDepartmentInput dept : orderedDepts) {
            String key = String.valueOf(dept.legacyId());
            String fp = sha256(dept.code() + "|" + dept.name() + "|" + dept.parentLegacyId() + "|" + dept.active());
            OrgMigrationResourceMapEntity existing = existingMaps.get("DEPARTMENT:" + key);
            UUID unitUuid;
            if (existing != null && existing.getTargetPublicId() != null) {
                unitUuid = UUID.fromString(existing.getTargetPublicId());
                if (!fp.equals(existing.getSourceFingerprint())) {
                    existing.setSourceFingerprint(fp);
                    existing.setLastRunId(run.getId());
                    mapMapper.updateById(existing);
                }
            } else {
                UUID parentUnitUuid = dept.parentLegacyId() != null ? deptToUnitUuid.get(dept.parentLegacyId()) : null;
                String unitCode = normalizeCode(dept.code(), "dept-" + dept.legacyId());
                OrgUnitModels.Response unit = structure.createUnit(new OrgUnitModels.CreateRequest(
                        orgUuid, parentUnitUuid, unitCode, dept.name().trim(),
                        dept.sortOrder() == null ? 0 : dept.sortOrder(),
                        BASE_EFFECTIVE_FROM, null, "migrated-dept:" + dept.legacyId()
                ));
                if (dept.active()) {
                    structure.transitionUnit(unit.publicId(),
                            new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));
                }
                unitUuid = unit.publicId();
                OrgUnitEntity unitEntity = mappers.units().selectOne(new QueryWrapper<OrgUnitEntity>()
                        .eq("public_id", unitUuid.toString()).eq("deleted", 0).last("LIMIT 1"));
                recordMap(mapMapper, snapshot.tenantId(), scope.getId(), organization.getId(), run.getId(),
                        "DEPARTMENT", key, "ORG_UNIT", unitEntity.getId(), unitUuid, unitCode, fp, 1);
            }
            deptToUnitUuid.put(dept.legacyId(), unitUuid);
            mappedCount++;
        }

        // Ensure a root fallback unit exists when an employee has no department
        UUID fallbackUnitUuid = deptToUnitUuid.values().stream().findFirst().orElseGet(() ->
                ensureFallbackUnit(snapshot.tenantId(), organization, scope.getId(), run.getId(), existingMaps, mapMapper));

        Map<Long, LegacyPositionInput> posById = new HashMap<>();
        for (LegacyPositionInput p : positions) {
            posById.put(p.legacyId(), p);
        }

        // 2. Migrate employees -> Person + Engagement + Unit-scoped Position + Assignment
        for (LegacyEmployeeInput emp : employees) {
            UUID unitUuid = emp.legacyDepartmentId() != null
                    ? deptToUnitUuid.getOrDefault(emp.legacyDepartmentId(), fallbackUnitUuid)
                    : fallbackUnitUuid;
            LegacyPositionInput posInput = emp.legacyPositionId() != null ? posById.get(emp.legacyPositionId()) : null;
            UUID posUuid = ensureUnitScopedPosition(snapshot.tenantId(), organization, scope.getId(), run.getId(),
                    unitUuid, posInput, existingMaps, mapMapper);

            String empKey = String.valueOf(emp.legacyId());
            String fp = sha256(emp.employeeNo() + "|" + emp.employeeName() + "|" + emp.legacyDepartmentId()
                    + "|" + emp.legacyPositionId() + "|" + emp.active());
            OrgMigrationResourceMapEntity existingEmpMap = existingMaps.get("EMPLOYEE:" + empKey);
            UUID personUuid;
            if (existingEmpMap != null && existingEmpMap.getTargetPublicId() != null) {
                personUuid = UUID.fromString(existingEmpMap.getTargetPublicId());
                if (!fp.equals(existingEmpMap.getSourceFingerprint())) {
                    existingEmpMap.setSourceFingerprint(fp);
                    existingEmpMap.setLastRunId(run.getId());
                    mapMapper.updateById(existingEmpMap);
                }
            } else {
                Instant effFrom = emp.effectiveFrom() != null && !emp.effectiveFrom().isBefore(BASE_EFFECTIVE_FROM)
                        ? emp.effectiveFrom() : BASE_EFFECTIVE_FROM;
                String personCode = normalizeCode(emp.employeeNo(), "emp-" + emp.legacyId());
                PersonModels.Response person = workforce.createPerson(new PersonModels.CreateRequest(
                        personCode, emp.employeeName().trim(), effFrom, null, "migrated-emp:" + emp.legacyId()
                ));
                if (emp.active()) {
                    person = workforce.transitionPerson(person.publicId(),
                            new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, person.version()));
                }
                personUuid = person.publicId();

                EngagementModels.Response engagement = workforce.createEngagement(new EngagementModels.CreateRequest(
                        "eng-" + personCode,
                        personUuid,
                        orgUuid,
                        new TypeDescriptorInput(ENGAGEMENT_TYPE_NS, "employment", "1.0.0"),
                        effFrom,
                        null,
                        "migrated-emp:" + emp.legacyId()
                ));
                if (emp.active()) {
                    engagement = workforce.transitionEngagement(engagement.publicId(),
                            new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, engagement.version()));
                }

                AssignmentModels.Response assignment = workforce.createAssignment(new AssignmentModels.CreateRequest(
                        engagement.publicId(),
                        unitUuid,
                        posUuid,
                        Boolean.TRUE,
                        effFrom,
                        null,
                        "migrated-emp:" + emp.legacyId()
                ));
                if (emp.active()) {
                    workforce.transitionAssignment(assignment.publicId(),
                            new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, assignment.version()));
                }

                PersonEntity personEntity = mappers.persons().selectOne(new QueryWrapper<PersonEntity>()
                        .eq("public_id", personUuid.toString()).eq("deleted", 0).last("LIMIT 1"));
                recordMap(mapMapper, snapshot.tenantId(), scope.getId(), organization.getId(), run.getId(),
                        "EMPLOYEE", empKey, "PERSON", personEntity.getId(), personUuid, personCode, fp, 1);
            }
            employeeToPerson.put(emp.legacyId(), personUuid);
            mappedCount++;
        }

        // Count legacy positions covered in snapshot as mapped once referenced/verified
        mappedCount += positions.size();
        return mappedCount;
    }

    private UUID ensureFallbackUnit(
            long tenantId,
            OrganizationEntity org,
            Long scopeId,
            Long runId,
            Map<String, OrgMigrationResourceMapEntity> existingMaps,
            OrgMigrationResourceMapMapper mapMapper
    ) {
        OrgMigrationResourceMapEntity existing = existingMaps.get("DEPARTMENT:default-root");
        if (existing != null && existing.getTargetPublicId() != null) {
            return UUID.fromString(existing.getTargetPublicId());
        }
        OrgUnitModels.Response unit = structure.createUnit(new OrgUnitModels.CreateRequest(
                UUID.fromString(org.getPublicId()), null, "root-unit", org.getDisplayName(), 0,
                BASE_EFFECTIVE_FROM, null, "migrated-default-root"
        ));
        structure.transitionUnit(unit.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));
        OrgUnitEntity entity = mappers.units().selectOne(new QueryWrapper<OrgUnitEntity>()
                .eq("public_id", unit.publicId().toString()).eq("deleted", 0).last("LIMIT 1"));
        OrgMigrationResourceMapEntity row = recordMap(mapMapper, tenantId, scopeId, org.getId(), runId,
                "DEPARTMENT", "default-root", "ORG_UNIT", entity.getId(), unit.publicId(), "root-unit",
                sha256("root-unit"), 1);
        existingMaps.put("DEPARTMENT:default-root", row);
        return unit.publicId();
    }

    private UUID ensureUnitScopedPosition(
            long tenantId,
            OrganizationEntity org,
            Long scopeId,
            Long runId,
            UUID unitUuid,
            LegacyPositionInput posInput,
            Map<String, OrgMigrationResourceMapEntity> existingMaps,
            OrgMigrationResourceMapMapper mapMapper
    ) {
        String sourceKey = (posInput == null ? "default" : posInput.legacyId()) + "@" + unitUuid;
        OrgMigrationResourceMapEntity existing = existingMaps.get("POSITION:" + sourceKey);
        if (existing != null && existing.getTargetPublicId() != null) {
            return UUID.fromString(existing.getTargetPublicId());
        }
        String rawCode = posInput != null ? posInput.code() : "member";
        String suffix = unitUuid.toString().substring(0, 8);
        String posCode = normalizeCode(rawCode + "-" + suffix, "pos-" + suffix);
        String displayName = posInput != null && posInput.name() != null && !posInput.name().isBlank()
                ? posInput.name().trim() : "Staff Member";
        PositionModels.Response created = structure.createPosition(new PositionModels.CreateRequest(
                UUID.fromString(org.getPublicId()), unitUuid, posCode, displayName,
                BASE_EFFECTIVE_FROM, null, "migrated-pos:" + sourceKey
        ));
        if (posInput == null || posInput.active()) {
            structure.transitionPosition(created.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, created.version()));
        }
        PositionEntity entity = mappers.positions().selectOne(new QueryWrapper<PositionEntity>()
                .eq("public_id", created.publicId().toString()).eq("deleted", 0).last("LIMIT 1"));
        OrgMigrationResourceMapEntity row = recordMap(mapMapper, tenantId, scopeId, org.getId(), runId,
                "POSITION", sourceKey, "POSITION", entity.getId(), created.publicId(), posCode,
                sha256(posCode + "|" + displayName), 1);
        existingMaps.put("POSITION:" + sourceKey, row);
        return created.publicId();
    }

    private int verifySnapshot(
            LegacyOrganizationSnapshot snapshot,
            Long targetOrgId,
            OrgMigrationScopeEntity scope,
            OrgMigrationRunEntity run,
            List<LegacyDepartmentInput> departments,
            List<LegacyPositionInput> positions,
            List<LegacyEmployeeInput> employees,
            OrgMigrationResourceMapMapper mapMapper,
            Map<Long, UUID> employeeToPerson,
            List<IssueSummary> issues
    ) {
        Map<String, OrgMigrationResourceMapEntity> maps = indexMaps(loadScopeMaps(mapMapper, scope.getId()));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int verified = 0;

        for (LegacyDepartmentInput dept : departments) {
            String key = String.valueOf(dept.legacyId());
            OrgMigrationResourceMapEntity map = maps.get("DEPARTMENT:" + key);
            if (map == null || map.getTargetPublicId() == null) {
                issues.add(new IssueSummary("DEPARTMENT", key, "MISSING_TARGET_MAPPING", "BLOCKING",
                        "Legacy department has not been migrated to org_unit"));
                continue;
            }
            OrgUnitEntity unit = mappers.units().selectOne(new QueryWrapper<OrgUnitEntity>()
                    .eq("public_id", map.getTargetPublicId()).eq("deleted", 0).last("LIMIT 1"));
            if (unit == null) {
                issues.add(new IssueSummary("DEPARTMENT", key, "MISSING_TARGET_UNIT", "BLOCKING",
                        "Mapped org_unit " + map.getTargetPublicId() + " no longer exists"));
                continue;
            }
            if (!Objects.equals(unit.getDisplayName(), dept.name() == null ? null : dept.name().trim())) {
                issues.add(new IssueSummary("DEPARTMENT", key, "DISPLAY_NAME_DRIFT", "WARNING",
                        "Legacy department name differs from org_unit display_name"));
            }
            map.setStatus("VERIFIED");
            map.setLastRunId(run.getId());
            map.setLastVerifiedAt(now);
            mapMapper.updateById(map);
            verified++;
        }

        for (LegacyEmployeeInput emp : employees) {
            String key = String.valueOf(emp.legacyId());
            OrgMigrationResourceMapEntity map = maps.get("EMPLOYEE:" + key);
            if (map == null || map.getTargetPublicId() == null) {
                issues.add(new IssueSummary("EMPLOYEE", key, "MISSING_TARGET_MAPPING", "BLOCKING",
                        "Legacy employee has not been migrated to org_person"));
                continue;
            }
            PersonEntity person = mappers.persons().selectOne(new QueryWrapper<PersonEntity>()
                    .eq("public_id", map.getTargetPublicId()).eq("deleted", 0).last("LIMIT 1"));
            if (person == null) {
                issues.add(new IssueSummary("EMPLOYEE", key, "MISSING_TARGET_PERSON", "BLOCKING",
                        "Mapped org_person " + map.getTargetPublicId() + " no longer exists"));
                continue;
            }
            if (!Objects.equals(person.getDisplayName(), emp.employeeName() == null ? null : emp.employeeName().trim())) {
                issues.add(new IssueSummary("EMPLOYEE", key, "DISPLAY_NAME_DRIFT", "WARNING",
                        "Legacy employee name differs from org_person display_name"));
            }
            map.setStatus("VERIFIED");
            map.setLastRunId(run.getId());
            map.setLastVerifiedAt(now);
            mapMapper.updateById(map);
            employeeToPerson.put(emp.legacyId(), UUID.fromString(person.getPublicId()));
            verified++;
        }

        verified += positions.size();
        return verified;
    }

    private List<LegacyDepartmentInput> topoSortDepartments(List<LegacyDepartmentInput> departments) {
        List<LegacyDepartmentInput> remaining = new ArrayList<>(departments);
        remaining.sort(Comparator.comparing(d -> d.parentLegacyId() == null ? 0L : d.parentLegacyId()));
        List<LegacyDepartmentInput> ordered = new ArrayList<>(departments.size());
        Set<Long> placed = new HashSet<>();
        boolean progressed = true;
        while (!remaining.isEmpty() && progressed) {
            progressed = false;
            var it = remaining.iterator();
            while (it.hasNext()) {
                LegacyDepartmentInput d = it.next();
                if (d.parentLegacyId() == null || d.parentLegacyId() <= 0 || placed.contains(d.parentLegacyId())) {
                    ordered.add(d);
                    placed.add(d.legacyId());
                    it.remove();
                    progressed = true;
                }
            }
        }
        ordered.addAll(remaining);
        return ordered;
    }

    private List<OrgMigrationResourceMapEntity> loadScopeMaps(OrgMigrationResourceMapMapper mapMapper, Long scopeId) {
        return mapMapper.selectList(new QueryWrapper<OrgMigrationResourceMapEntity>()
                .eq("migration_scope_id", scopeId)
                .eq("deleted", 0));
    }

    private Map<String, OrgMigrationResourceMapEntity> indexMaps(List<OrgMigrationResourceMapEntity> list) {
        Map<String, OrgMigrationResourceMapEntity> map = new HashMap<>();
        for (OrgMigrationResourceMapEntity item : list) {
            map.put(item.getSourceResourceType() + ":" + item.getSourceResourceKey(), item);
        }
        return map;
    }

    private OrgMigrationResourceMapEntity recordMap(
            OrgMigrationResourceMapMapper mapMapper,
            long tenantId,
            Long scopeId,
            Long targetOrgId,
            Long runId,
            String sourceType,
            String sourceKey,
            String targetType,
            Long targetResourceId,
            UUID targetPublicId,
            String plannedCode,
            String sourceFingerprint,
            int targetVersion
    ) {
        OrgMigrationResourceMapEntity row = new OrgMigrationResourceMapEntity();
        row.setTenantId(tenantId);
        row.setPublicId(uuidGenerator.generate().toString());
        row.setMigrationScopeId(scopeId);
        row.setSourceNamespace(SOURCE_NAMESPACE);
        row.setSourceResourceType(sourceType);
        row.setSourceResourceKey(sourceKey);
        row.setTargetResourceType(targetType);
        row.setTargetOrganizationId(targetOrgId);
        row.setTargetResourceId(targetResourceId);
        row.setTargetPublicId(targetPublicId.toString());
        row.setPlannedResourceCode(plannedCode);
        row.setMappingOrigin("GENERATED");
        row.setManagedTarget(Boolean.TRUE);
        row.setSourceFingerprint(sourceFingerprint);
        row.setTargetFingerprint(sourceFingerprint);
        row.setTargetVersion(targetVersion);
        row.setLastRunId(runId);
        row.setStatus("MAPPED");
        row.setVersion(0);
        row.setDeleted(0);
        mapMapper.insert(row);
        return row;
    }

    private static String normalizeCode(String raw, String fallback) {
        String candidate = (raw == null || raw.isBlank()) ? fallback : raw.trim();
        String cleaned = candidate.replaceAll("[^A-Za-z0-9.:-]", "-");
        if (cleaned.isEmpty() || !Character.isLetterOrDigit(cleaned.charAt(0))) {
            cleaned = "x-" + cleaned;
        }
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16))
                        .append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private <T> T require(ObjectProvider<T> provider) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Organization migration requires the db profile");
        }
        return bean;
    }
}
