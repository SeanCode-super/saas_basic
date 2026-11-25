package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.api.UserPersonDirectory;
import com.saasbasics.platform.modules.iam.api.model.UserPersonBindingModels;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.LegacyDepartmentInput;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.LegacyEmployeeInput;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.LegacyOrganizationSnapshot;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.LegacyPositionInput;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.MigrationMode;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.MigrationReport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates repeatable legacy iam_department/iam_position/iam_employee migration into the standard
 * Organization core and idempotent backfill of iam_user_person_binding.
 */
@Service
public class OrganizationLegacyMigrationService {

    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<PositionMapper> positionMapperProvider;
    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final OrganizationDirectory organizationDirectory;
    private final OrganizationMigrationFacade migrationFacade;
    private final UserPersonBindingService userPersonBindingService;
    private final UserPersonDirectory userPersonDirectory;
    private final AccessSubjectProfileService subjectProfileService;

    public OrganizationLegacyMigrationService(
            ObjectProvider<DepartmentMapper> departmentMapperProvider,
            ObjectProvider<PositionMapper> positionMapperProvider,
            ObjectProvider<EmployeeMapper> employeeMapperProvider,
            ObjectProvider<UserMapper> userMapperProvider,
            OrganizationDirectory organizationDirectory,
            OrganizationMigrationFacade migrationFacade,
            UserPersonBindingService userPersonBindingService,
            UserPersonDirectory userPersonDirectory,
            AccessSubjectProfileService subjectProfileService
    ) {
        this.departmentMapperProvider = departmentMapperProvider;
        this.positionMapperProvider = positionMapperProvider;
        this.employeeMapperProvider = employeeMapperProvider;
        this.userMapperProvider = userMapperProvider;
        this.organizationDirectory = organizationDirectory;
        this.migrationFacade = migrationFacade;
        this.userPersonBindingService = userPersonBindingService;
        this.userPersonDirectory = userPersonDirectory;
        this.subjectProfileService = subjectProfileService;
    }

    public record LegacyMigrationSummary(
            MigrationReport organizationReport,
            int userBindingsCreated,
            int userBindingsAlreadyPresent,
            int dualReadMatches,
            int dualReadMismatches
    ) {
    }

    @Transactional
    public LegacyMigrationSummary runMigration(MigrationMode mode, String organizationCode, String organizationName) {
        long tenantId = TenantAccessContextHolder.requiredTenantId();
        AuthPrincipal principal = AuthContext.get();
        String tenantCode = principal != null && principal.tenantCode() != null
                ? principal.tenantCode() : "tenant-" + tenantId;

        List<DepartmentEntity> departments = require(departmentMapperProvider).selectList(
                new LambdaQueryWrapper<DepartmentEntity>().eq(DepartmentEntity::getDeleted, 0));
        List<PositionEntity> positions = require(positionMapperProvider).selectList(
                new LambdaQueryWrapper<PositionEntity>().eq(PositionEntity::getDeleted, 0));
        List<EmployeeEntity> employees = require(employeeMapperProvider).selectList(
                new LambdaQueryWrapper<EmployeeEntity>().eq(EmployeeEntity::getDeleted, 0));

        LegacyOrganizationSnapshot snapshot = new LegacyOrganizationSnapshot(
                tenantId,
                tenantCode,
                (organizationCode == null || organizationCode.isBlank()) ? tenantCode : organizationCode.trim(),
                (organizationName == null || organizationName.isBlank()) ? tenantCode : organizationName.trim(),
                departments.stream().map(d -> new LegacyDepartmentInput(
                        d.getId(), d.getParentId(), d.getDeptCode(), d.getDeptName(), d.getSortNo(),
                        !"DISABLED".equalsIgnoreCase(d.getStatus())
                )).toList(),
                positions.stream().map(p -> new LegacyPositionInput(
                        p.getId(), p.getPositionCode(), p.getPositionName(),
                        !"DISABLED".equalsIgnoreCase(p.getStatus())
                )).toList(),
                employees.stream().map(e -> new LegacyEmployeeInput(
                        e.getId(),
                        e.getEmployeeNo(),
                        e.getEmployeeName(),
                        e.getDeptId(),
                        e.getPositionId(),
                        e.getHireDate() != null ? e.getHireDate().atStartOfDay().toInstant(ZoneOffset.UTC) : null,
                        !"RESIGNED".equalsIgnoreCase(e.getEmployeeStatus()) && !"DISABLED".equalsIgnoreCase(e.getEmployeeStatus())
                )).toList()
        );

        MigrationReport report = migrationFacade.execute(snapshot, mode);

        int createdBindings = 0;
        int existingBindings = 0;
        int dualReadMatches = 0;
        int dualReadMismatches = 0;

        if ((mode == MigrationMode.APPLY || mode == MigrationMode.VERIFY) && !report.employeeToPersonPublicIds().isEmpty()) {
            List<UserEntity> users = require(userMapperProvider).selectList(
                    new LambdaQueryWrapper<UserEntity>()
                            .eq(UserEntity::getDeleted, 0)
                            .isNotNull(UserEntity::getEmployeeId)
                            .gt(UserEntity::getEmployeeId, 0L));
            Map<Long, UUID> empToPerson = new LinkedHashMap<>(report.employeeToPersonPublicIds());
            Instant now = Instant.now();

            for (UserEntity user : users) {
                UUID expectedPerson = empToPerson.get(user.getEmployeeId());
                if (expectedPerson == null || user.getPublicId() == null) {
                    continue;
                }
                UUID userUuid = UUID.fromString(user.getPublicId());
                Optional<UserPersonDirectory.BindingSummary> active = userPersonDirectory.findEffectiveByUser(userUuid, now);
                if (active.isPresent()) {
                    existingBindings++;
                    if (expectedPerson.equals(active.get().personPublicId())) {
                        dualReadMatches++;
                    } else {
                        dualReadMismatches++;
                    }
                    continue;
                }
                if (mode == MigrationMode.APPLY) {
                    OrganizationDirectory.PersonSummary person = organizationDirectory.getPerson(expectedPerson);
                    Instant bindingValidFrom = (person != null && person.validFrom() != null)
                            ? person.validFrom()
                            : Instant.parse("2020-01-01T00:00:00Z");
                    Instant bindingValidTo = person != null ? person.validTo() : null;
                    var created = userPersonBindingService.create(new UserPersonBindingModels.CreateRequest(
                            userUuid, expectedPerson, bindingValidFrom, bindingValidTo,
                            "legacy-employee-backfill:" + user.getEmployeeId()
                    ));
                    userPersonBindingService.transition(created.publicId(),
                            new UserPersonBindingModels.TransitionRequest(UserPersonBindingModels.Status.ACTIVE, created.version()));
                    createdBindings++;
                    var profile = subjectProfileService.load(tenantId, user.getId());
                    if (expectedPerson.equals(profile.personPublicId())) {
                        dualReadMatches++;
                    } else {
                        dualReadMismatches++;
                    }
                } else if (mode == MigrationMode.VERIFY) {
                    dualReadMismatches++;
                }
            }
        }

        return new LegacyMigrationSummary(report, createdBindings, existingBindings, dualReadMatches, dualReadMismatches);
    }

    private <T> T require(ObjectProvider<T> provider) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Organization migration requires the db profile");
        }
        return bean;
    }
}
