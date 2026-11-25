package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.entity.UserRoleEntity;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import com.saasbasics.platform.modules.iam.service.AccessSubjectProfileService;
import com.saasbasics.platform.modules.iam.service.DataPermissionRuleService;
import com.saasbasics.platform.modules.iam.service.IamService;
import com.saasbasics.platform.modules.iam.service.OrganizationLegacyMigrationService;
import com.saasbasics.platform.modules.iam.service.UserService;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.organization.api.OrganizationMigrationFacade.MigrationMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@Sql(statements = """
        INSERT INTO plat_tenant_quota (tenant_id, quota_code, quota_name, quota_value)
        VALUES (5101, 'DEPT_COUNT', 'Departments', 20);
        """, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(classes = SaasBasicsApplication.class)
class OrganizationLegacyMigrationIntegrationTest {

    private static final long TENANT_ID = 5101L;
    private static final UuidV7Generator UUID_GENERATOR = new UuidV7Generator();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private PositionMapper positionMapper;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private UserRoleMapper userRoleMapper;
    @Autowired private OrganizationLegacyMigrationService migrationService;
    @Autowired private AccessSubjectProfileService subjectProfileService;
    @Autowired private OrganizationDirectory organizationDirectory;
    @Autowired private DataPermissionRuleService dataPermissionRuleService;
    @Autowired private UserService userService;
    @Autowired private IamService iamService;

    @AfterEach
    void cleanup() {
        DataPermissionContext.clear();
        AuthContext.clear();
        TenantAccessContextHolder.clear();
        subjectProfileService.setLegacyEmployeeFallbackEnabled(true);
    }

    @Test
    void validatesAppliesIdempotentlyVerifiesParityAndResolvesStandardDataScopeWithoutLegacyFallback() {
        var actor = new AuthPrincipal(1L, TENANT_ID, "mig5101", 1L, "admin", "Admin",
                "TENANT", "ONLINE", null, List.of("*"), List.of(), List.of(), List.of());
        AuthContext.set(actor);
        try (var ignored = TenantAccessContextHolder.openAuthenticated(actor)) {
            DepartmentEntity rootDept = new DepartmentEntity();
            rootDept.setParentId(0L);
            rootDept.setDeptCode("hq_root");
            rootDept.setDeptName("HQ Root");
            rootDept.setStatus("ENABLED");
            rootDept.setSortNo(1);
            departmentMapper.insert(rootDept);

            DepartmentEntity childDept = new DepartmentEntity();
            childDept.setParentId(rootDept.getId());
            childDept.setDeptCode("rnd_child");
            childDept.setDeptName("R&D Child");
            childDept.setStatus("ENABLED");
            childDept.setSortNo(2);
            departmentMapper.insert(childDept);

            PositionEntity pos = new PositionEntity();
            pos.setPositionCode("eng_lead");
            pos.setPositionName("Engineering Lead");
            pos.setPositionLevel("P7");
            pos.setStatus("ENABLED");
            pos.setSortNo(1);
            positionMapper.insert(pos);

            EmployeeEntity emp = new EmployeeEntity();
            emp.setEmployeeNo("EMP_5101");
            emp.setEmployeeName("Zhang Wei");
            emp.setDeptId(rootDept.getId());
            emp.setPositionId(pos.getId());
            emp.setHireDate(LocalDate.of(2024, 3, 1));
            emp.setEmployeeStatus("ACTIVE");
            employeeMapper.insert(emp);

            UserEntity user = new UserEntity();
            user.setPublicId(UUID_GENERATOR.generate().toString());
            user.setUserCode("u_5101");
            user.setUsername("zhangwei.5101");
            user.setNickname("Zhang Wei");
            user.setEmployeeId(emp.getId());
            user.setUserType("STAFF");
            user.setStatus("ENABLED");
            user.setPasswordHash("hashed");
            user.setNeedResetPassword(false);
            userMapper.insert(user);

            RoleEntity orgUnitSubRole = new RoleEntity();
            orgUnitSubRole.setRoleCode("org_unit_sub_role");
            orgUnitSubRole.setRoleName("OrgUnit Subtree Role");
            orgUnitSubRole.setDataScopeType("ORG_UNIT_AND_SUB");
            orgUnitSubRole.setStatus("ENABLED");
            roleMapper.insert(orgUnitSubRole);

            UserRoleEntity userRole = new UserRoleEntity();
            userRole.setUserId(user.getId());
            userRole.setRoleId(orgUnitSubRole.getId());
            userRoleMapper.insert(userRole);

            // 1. VALIDATE dry-run
            var validateSummary = migrationService.runMigration(MigrationMode.VALIDATE, "mig-5101", "Migration 5101 Org");
            assertThat(validateSummary.organizationReport().status()).isEqualTo("SUCCEEDED");
            assertThat(validateSummary.organizationReport().blockingIssueCount()).isZero();

            // 2. APPLY backfill (creates Org, 2 OrgUnits, Position, Person, Engagement, Assignment, UserPersonBinding)
            var applySummary = migrationService.runMigration(MigrationMode.APPLY, "mig-5101", "Migration 5101 Org");
            assertThat(applySummary.organizationReport().status()).isEqualTo("SUCCEEDED");
            assertThat(applySummary.userBindingsCreated()).isEqualTo(1);
            assertThat(applySummary.dualReadMatches()).isEqualTo(1);
            assertThat(applySummary.dualReadMismatches()).isZero();

            // 3. Repeat APPLY must be idempotent
            var repeatSummary = migrationService.runMigration(MigrationMode.APPLY, "mig-5101", "Migration 5101 Org");
            assertThat(repeatSummary.userBindingsCreated()).isZero();
            assertThat(repeatSummary.userBindingsAlreadyPresent()).isEqualTo(1);
            assertThat(repeatSummary.dualReadMismatches()).isZero();

            // 4. VERIFY dual-read parity
            var verifySummary = migrationService.runMigration(MigrationMode.VERIFY, "mig-5101", "Migration 5101 Org");
            assertThat(verifySummary.organizationReport().status()).isEqualTo("SUCCEEDED");
            assertThat(verifySummary.organizationReport().blockingIssueCount()).isZero();
            assertThat(verifySummary.dualReadMatches()).isEqualTo(1);
            assertThat(verifySummary.dualReadMismatches()).isZero();

            // 5. Cutover check: disable legacy iam_user.employee_id fallback and verify standard resolution + DataScope
            subjectProfileService.setLegacyEmployeeFallbackEnabled(false);
            AccessSubjectProfile profile = subjectProfileService.load(TENANT_ID, user.getId());
            assertThat(profile.legacyFallbackUsed()).isFalse();
            assertThat(profile.employeeId()).isNull();
            assertThat(profile.personPublicId()).isNotNull();
            assertThat(profile.organizationPublicId()).isEqualTo(applySummary.organizationReport().organizationPublicId());
            assertThat(profile.orgUnitPublicId()).isNotNull();
            assertThat(profile.assignmentPublicId()).isNotNull();

            // Subtree of root orgUnit includes both root and child orgUnits (2 UUIDs)
            assertThat(organizationDirectory.expandOrgUnitSubtree(profile.orgUnitPublicId())).hasSize(2);

            var userPrincipal = new AuthPrincipal(1L, TENANT_ID, "mig5101", user.getId(), user.getUsername(),
                    user.getNickname(), "STAFF", "ONLINE", null, List.of("crm:customer:query"),
                    List.of(), List.of(orgUnitSubRole.getId()), List.of());
            AuthContext.set(userPrincipal);
            DataPermissionSqlSpec spec = dataPermissionRuleService.resolveDataPermission("crm:customer:query");
            assertThat(spec.getOrgUnitPublicIds()).hasSize(2);
            assertThat(organizationDirectory.findEffectiveAssignments(profile.personPublicId(), Instant.now())).hasSize(1);

            // 6. Re-entrancy & SQL safety check for non-super-admin @RequireDataPermission endpoints
            DataPermissionContext.setResourceCode("iam:user:list");
            try {
                assertThat(userService.listUsers()).isNotEmpty();
                DataPermissionContext.setResourceCode("iam:department:list");
                assertThat(iamService.departments()).isEmpty();
                DataPermissionContext.setResourceCode("iam:position:list");
                assertThat(iamService.positions()).isEmpty();
                DataPermissionContext.setResourceCode("iam:employee:list");
                assertThat(iamService.employees()).isNotEmpty();

                subjectProfileService.setLegacyEmployeeFallbackEnabled(true);
                DataPermissionContext.setResourceCode("iam:department:list");
                assertThat(iamService.departments()).hasSize(2);
                DataPermissionContext.setResourceCode("iam:user:list");
                assertThat(userService.listUsers()).isNotEmpty();
            } finally {
                DataPermissionContext.clear();
            }
        }
    }
}
