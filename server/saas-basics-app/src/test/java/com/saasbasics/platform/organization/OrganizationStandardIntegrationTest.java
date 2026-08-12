package com.saasbasics.platform.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import com.saasbasics.platform.modules.organization.api.model.AssignmentModels;
import com.saasbasics.platform.modules.organization.api.model.EngagementModels;
import com.saasbasics.platform.modules.organization.api.model.OrgUnitModels;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.LifecycleTransitionRequest;
import com.saasbasics.platform.modules.organization.api.model.OrganizationCommonModels.TypeDescriptorInput;
import com.saasbasics.platform.modules.organization.api.model.OrganizationModels;
import com.saasbasics.platform.modules.organization.api.model.PersonModels;
import com.saasbasics.platform.modules.organization.api.model.PositionModels;
import com.saasbasics.platform.modules.organization.domain.model.LifecycleStatus;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationCatalogService;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationStructureService;
import com.saasbasics.platform.modules.organization.internal.application.OrganizationWorkforceService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = SaasBasicsApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class OrganizationStandardIntegrationTest {

    private static final long TENANT_ALPHA = 301L;
    private static final long TENANT_BETA = 302L;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

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

    @Autowired
    private OrganizationCatalogService catalog;

    @Autowired
    private OrganizationStructureService structure;

    @Autowired
    private OrganizationWorkforceService workforce;

    @Autowired
    private OrganizationDirectory directory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantAccessContextHolder.clear();
    }

    @Test
    void completesTheStandardOrganizationAssignmentLifecycle() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(TENANT_ALPHA, "alpha", 11L)) {
            OrganizationModels.Response organization = catalog.createOrganization(
                    new OrganizationModels.CreateRequest("org-alpha", "Alpha", null, START, null, null));
            organization = catalog.transitionOrganization(
                    organization.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, organization.version()));
            assertThat(organization.publicId().version()).isEqualTo(7);

            OrgUnitModels.Response unit = structure.createUnit(
                    new OrgUnitModels.CreateRequest(organization.publicId(), null, "unit-root", "Root", 0, START, null, null));
            unit = structure.transitionUnit(unit.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));

            PositionModels.Response position = structure.createPosition(
                    new PositionModels.CreateRequest(organization.publicId(), unit.publicId(), "position-owner", "Owner", START, null, null));
            position = structure.transitionPosition(
                    position.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, position.version()));

            PersonModels.Response person = workforce.createPerson(
                    new PersonModels.CreateRequest("person-alex", "Alex", START, null, null));
            person = workforce.transitionPerson(
                    person.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, person.version()));

            EngagementModels.Response engagement = workforce.createEngagement(
                    new EngagementModels.CreateRequest(
                            "engagement-alex-alpha",
                            person.publicId(),
                            organization.publicId(),
                            new TypeDescriptorInput("urn:saas-basics:organization", "MEMBER_OF", "1.0.0"),
                            START,
                            null,
                            null));
            engagement = workforce.transitionEngagement(
                    engagement.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, engagement.version()));

            AssignmentModels.Response assignment = workforce.createAssignment(
                    new AssignmentModels.CreateRequest(
                            engagement.publicId(), unit.publicId(), position.publicId(), true, START, null, null));
            assignment = workforce.transitionAssignment(
                    assignment.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, assignment.version()));
            UUID assignmentPublicId = assignment.publicId();

            assertThat(directory.findEffectiveAssignments(person.publicId(), START.plusSeconds(1)))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.publicId()).isEqualTo(assignmentPublicId);
                        assertThat(summary.primary()).isTrue();
                    });

            Instant termination = START.plusSeconds(86_400);
            AssignmentModels.Response terminated = workforce.terminateAssignment(
                    assignment.publicId(), termination, assignment.version());

            assertThat(terminated.status()).isEqualTo(LifecycleStatus.INACTIVE);
            assertThat(directory.findEffectiveAssignments(person.publicId(), termination)).isEmpty();
        }
    }

    @Test
    void organizationPublicIdsAndRelationsCannotCrossTenantBoundaries() {
        UUID alphaOrganization;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(TENANT_ALPHA, "alpha", 11L)) {
            alphaOrganization = catalog.createOrganization(
                    new OrganizationModels.CreateRequest("alpha-private", "Alpha Private", null, START, null, null))
                    .publicId();
        }

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(TENANT_BETA, "beta", 12L)) {
            assertThatThrownBy(() -> catalog.getOrganization(alphaOrganization))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("ORG_ORGANIZATION_NOT_FOUND");

            PersonModels.Response betaPerson = workforce.createPerson(
                    new PersonModels.CreateRequest("beta-person", "Beta Person", START, null, null));
            assertThatThrownBy(() -> workforce.createEngagement(
                    new EngagementModels.CreateRequest(
                            "cross-tenant-engagement",
                            betaPerson.publicId(),
                            alphaOrganization,
                            new TypeDescriptorInput("urn:saas-basics:organization", "MEMBER_OF", "1.0.0"),
                            START,
                            null,
                            null)))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("ORG_ORGANIZATION_NOT_FOUND");
        }
    }

    @Test
    void rejectsTreeCyclesCrossOrganizationAssignmentsAndOverlappingPrimaryPeriods() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(TENANT_ALPHA, "alpha", 11L)) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            StructureFixture alpha = activeStructure("alpha-" + suffix);
            StructureFixture beta = activeStructure("beta-" + suffix);
            PersonModels.Response person = activePerson("person-" + suffix);
            EngagementModels.Response engagement = activeEngagement("engagement-" + suffix, person, alpha.organization());

            AssignmentModels.Response primary = workforce.createAssignment(
                    new AssignmentModels.CreateRequest(
                            engagement.publicId(), alpha.unit().publicId(), alpha.position().publicId(), true, START, null, null));
            workforce.transitionAssignment(
                    primary.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, primary.version()));

            AssignmentModels.Response overlapping = workforce.createAssignment(
                    new AssignmentModels.CreateRequest(
                            engagement.publicId(), alpha.unit().publicId(), alpha.position().publicId(), true,
                            START.plusSeconds(60), null, null));
            assertThatThrownBy(() -> workforce.transitionAssignment(
                    overlapping.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, overlapping.version())))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("ORG_PRIMARY_ASSIGNMENT_CONFLICT");

            assertThatThrownBy(() -> workforce.createAssignment(
                    new AssignmentModels.CreateRequest(
                            engagement.publicId(), beta.unit().publicId(), beta.position().publicId(), false, START, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("ORG_CROSS_ORGANIZATION_REFERENCE");

            OrgUnitModels.Response child = structure.createUnit(
                    new OrgUnitModels.CreateRequest(
                            alpha.organization().publicId(), alpha.unit().publicId(), "child-" + suffix, "Child",
                            0, START, null, null));
            assertThatThrownBy(() -> structure.moveUnit(
                    alpha.unit().publicId(), new OrgUnitModels.MoveRequest(child.publicId(), alpha.unit().version())))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("ORG_UNIT_CYCLE");
        }
    }

    @Test
    void rejectsInternallyInconsistentAssignmentContextsFromImportedData() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_ALPHA, "alpha", 11L)) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            StructureFixture alpha = activeStructure("consistent-alpha-" + suffix);
            StructureFixture beta = activeStructure("consistent-beta-" + suffix);
            PersonModels.Response person = activePerson("consistent-person-" + suffix);
            EngagementModels.Response engagement = activeEngagement(
                    "consistent-engagement-" + suffix, person, alpha.organization());
            AssignmentModels.Response assignment = workforce.createAssignment(
                    new AssignmentModels.CreateRequest(
                            engagement.publicId(), alpha.unit().publicId(), alpha.position().publicId(), true,
                            START, null, null));
            assignment = workforce.transitionAssignment(
                    assignment.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, assignment.version()));

            assertThat(jdbcTemplate.update("""
                    UPDATE org_assignment assignment_record
                    INNER JOIN org_organization target_organization
                      ON target_organization.tenant_id = assignment_record.tenant_id
                     AND target_organization.public_id = ?
                     AND target_organization.deleted = 0
                    SET assignment_record.organization_id = target_organization.id
                    WHERE assignment_record.tenant_id = ?
                      AND assignment_record.public_id = ?
                      AND assignment_record.deleted = 0
                    """, beta.organization().publicId().toString(), TENANT_ALPHA,
                    assignment.publicId().toString())).isEqualTo(1);

            UUID assignmentPublicId = assignment.publicId();
            assertBizCode(
                    () -> directory.requireEffectiveAssignmentForPerson(
                            person.publicId(), assignmentPublicId, START.plusSeconds(1)),
                    "ORG_ASSIGNMENT_CONTEXT_INCONSISTENT");
            assertBizCode(
                    () -> directory.findEffectiveAssignments(person.publicId(), START.plusSeconds(1)),
                    "ORG_ASSIGNMENT_CONTEXT_INCONSISTENT");
            assertBizCode(
                    () -> directory.validateContext(
                            new OrganizationDirectory.ContextSelection(
                                    beta.organization().publicId(), engagement.publicId(), alpha.unit().publicId(),
                                    alpha.position().publicId(), assignmentPublicId),
                            START.plusSeconds(1)),
                    "ORG_ASSIGNMENT_CONTEXT_INCONSISTENT");
        }
    }

    private StructureFixture activeStructure(String code) {
        OrganizationModels.Response organization = catalog.createOrganization(
                new OrganizationModels.CreateRequest(code, code, null, START, null, null));
        organization = catalog.transitionOrganization(
                organization.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, organization.version()));
        OrgUnitModels.Response unit = structure.createUnit(
                new OrgUnitModels.CreateRequest(
                        organization.publicId(), null, code + "-unit", code + " Unit", 0, START, null, null));
        unit = structure.transitionUnit(
                unit.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));
        PositionModels.Response position = structure.createPosition(
                new PositionModels.CreateRequest(
                        organization.publicId(), unit.publicId(), code + "-position", code + " Position", START, null, null));
        position = structure.transitionPosition(
                position.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, position.version()));
        return new StructureFixture(organization, unit, position);
    }

    private PersonModels.Response activePerson(String code) {
        PersonModels.Response person = workforce.createPerson(
                new PersonModels.CreateRequest(code, code, START, null, null));
        return workforce.transitionPerson(
                person.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, person.version()));
    }

    private EngagementModels.Response activeEngagement(String code,
                                                        PersonModels.Response person,
                                                        OrganizationModels.Response organization) {
        EngagementModels.Response engagement = workforce.createEngagement(
                new EngagementModels.CreateRequest(
                        code,
                        person.publicId(),
                        organization.publicId(),
                        new TypeDescriptorInput("urn:saas-basics:organization", "MEMBER_OF", "1.0.0"),
                        START,
                        null,
                        null));
        return workforce.transitionEngagement(
                engagement.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, engagement.version()));
    }

    private void assertBizCode(Runnable operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo(expectedCode);
    }

    private record StructureFixture(
            OrganizationModels.Response organization,
            OrgUnitModels.Response unit,
            PositionModels.Response position
    ) {
    }
}
