package com.saasbasics.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.api.UserPersonDirectory;
import com.saasbasics.platform.modules.iam.api.model.UserPersonBindingModels;
import com.saasbasics.platform.modules.iam.dto.AuthContextSelectionRequest;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.service.AuthService;
import com.saasbasics.platform.modules.iam.service.UserPersonBindingService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = SaasBasicsApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class UserPersonBindingIntegrationTest {

    private static final long TENANT_BINDINGS = 4_101L;
    private static final long TENANT_ALPHA = 4_201L;
    private static final long TENANT_BETA = 4_202L;
    private static final long TENANT_CONTEXT = 4_301L;
    private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");
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

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserPersonBindingService bindings;

    @Autowired
    private UserPersonDirectory bindingDirectory;

    @Autowired
    private AuthService authService;

    @Autowired
    private OrganizationCatalogService catalog;

    @Autowired
    private OrganizationStructureService structure;

    @Autowired
    private OrganizationWorkforceService workforce;

    @AfterEach
    void clearContexts() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
    }

    @Test
    void enforcesOneActiveBindingPerAccountAndAllowsMultipleAccountsForOnePerson() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_BINDINGS, "bindings", 41L)) {
            PersonModels.Response person = activePerson("shared-person");
            UserEntity firstAccount = createUser("first-account", "STAFF");
            UserEntity secondAccount = createUser("second-account", "STAFF");

            UserPersonBindingModels.Response first = activateBinding(
                    firstAccount, person, START, Instant.parse("2030-01-01T00:00:00Z"));
            UserPersonBindingModels.Response secondAccountBinding = activateBinding(
                    secondAccount, person, START, null);

            assertThat(bindingDirectory.findEffectiveByPerson(person.publicId(), Instant.now()))
                    .extracting(UserPersonDirectory.BindingSummary::userPublicId)
                    .containsExactlyInAnyOrder(
                            UUID.fromString(firstAccount.getPublicId()),
                            UUID.fromString(secondAccount.getPublicId()));
            assertThat(bindings.list(
                    1, 20, null, person.publicId(), UserPersonBindingModels.Status.ACTIVE, Instant.now()).records())
                    .hasSize(2);

            UserPersonBindingModels.Response futureDraft = bindings.create(
                    new UserPersonBindingModels.CreateRequest(
                            UUID.fromString(firstAccount.getPublicId()),
                            person.publicId(),
                            Instant.parse("2030-01-01T00:00:00Z"),
                            null,
                            "future identity binding"));
            UserPersonBindingModels.Response reviewedDraft = bindings.update(
                    futureDraft.publicId(),
                    new UserPersonBindingModels.UpdateRequest(
                            UUID.fromString(firstAccount.getPublicId()),
                            person.publicId(),
                            Instant.parse("2031-01-01T00:00:00Z"),
                            null,
                            "reviewed future identity binding",
                            futureDraft.version()));
            assertThat(reviewedDraft.validFrom()).isEqualTo(Instant.parse("2031-01-01T00:00:00Z"));
            assertThat(reviewedDraft.remark()).isEqualTo("reviewed future identity binding");
            UserPersonBindingModels.Response currentDraft = reviewedDraft;
            assertBizCode(
                    () -> bindings.update(
                            currentDraft.publicId(),
                            new UserPersonBindingModels.UpdateRequest(
                                    currentDraft.userPublicId(),
                                    currentDraft.personPublicId(),
                                    currentDraft.validFrom(),
                                    currentDraft.validTo(),
                                    currentDraft.remark(),
                                    currentDraft.version() - 1)),
                    "IAM_USER_PERSON_VERSION_CONFLICT");

            assertThatThrownBy(() -> bindings.transition(
                    reviewedDraft.publicId(),
                    new UserPersonBindingModels.TransitionRequest(
                            UserPersonBindingModels.Status.ACTIVE, reviewedDraft.version())))
                    .isInstanceOf(BizException.class)
                    .extracting(exception -> ((BizException) exception).getCode())
                    .isEqualTo("IAM_USER_PERSON_ACTIVE_CONFLICT");

            first = bindings.transition(
                    first.publicId(),
                    new UserPersonBindingModels.TransitionRequest(
                            UserPersonBindingModels.Status.INACTIVE, first.version()));
            UserPersonBindingModels.Response futureActive = bindings.transition(
                    reviewedDraft.publicId(),
                    new UserPersonBindingModels.TransitionRequest(
                            UserPersonBindingModels.Status.ACTIVE, reviewedDraft.version()));

            assertThat(first.status()).isEqualTo(UserPersonBindingModels.Status.INACTIVE);
            assertThat(futureActive.status()).isEqualTo(UserPersonBindingModels.Status.ACTIVE);
            UserPersonBindingModels.Response activeBinding = futureActive;
            assertBizCode(
                    () -> bindings.update(
                            activeBinding.publicId(),
                            new UserPersonBindingModels.UpdateRequest(
                                    activeBinding.userPublicId(),
                                    activeBinding.personPublicId(),
                                    activeBinding.validFrom(),
                                    activeBinding.validTo(),
                                    activeBinding.remark(),
                                    activeBinding.version())),
                    "IAM_USER_PERSON_UPDATE_REQUIRES_DRAFT");
            assertThat(bindings.get(secondAccountBinding.publicId())).isEqualTo(secondAccountBinding);
            assertThat(bindings.list(1, 20).records()).hasSize(3);

            UserPersonBindingModels.Response futureInactive = bindings.transition(
                    futureActive.publicId(),
                    new UserPersonBindingModels.TransitionRequest(
                            UserPersonBindingModels.Status.INACTIVE, futureActive.version()));
            UserPersonBindingModels.Response futureArchived = bindings.transition(
                    futureInactive.publicId(),
                    new UserPersonBindingModels.TransitionRequest(
                            UserPersonBindingModels.Status.ARCHIVED, futureInactive.version()));
            bindings.delete(futureArchived.publicId(), futureArchived.version());

            assertBizCode(() -> bindings.get(futureArchived.publicId()), "IAM_USER_PERSON_BINDING_NOT_FOUND");
            assertThat(bindings.list(1, 20).records()).hasSize(2);
        }
    }

    @Test
    void rejectsCrossTenantBindingAccessAndKeepsServiceAccountsPersonless() {
        UUID alphaUserPublicId;
        UUID alphaPersonPublicId;
        UUID alphaBindingPublicId;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_ALPHA, "alpha", 42L)) {
            PersonModels.Response person = activePerson("alpha-person");
            UserEntity user = createUser("alpha-user", "STAFF");
            UserPersonBindingModels.Response binding = activateBinding(user, person, START, null);
            alphaUserPublicId = UUID.fromString(user.getPublicId());
            alphaPersonPublicId = person.publicId();
            alphaBindingPublicId = binding.publicId();
        }

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_BETA, "beta", 43L)) {
            UserEntity serviceAccount = createUser("automation", "SERVICE");
            UUID serviceAccountPublicId = UUID.fromString(serviceAccount.getPublicId());

            assertThat(bindingDirectory.findEffectiveByUser(serviceAccountPublicId, Instant.now())).isEmpty();
            assertThat(bindings.list(1, 20).records()).isEmpty();

            AuthContext.set(new AuthPrincipal(
                    87_654L,
                    TENANT_BETA,
                    "beta",
                    serviceAccount.getId(),
                    serviceAccount.getUsername(),
                    serviceAccount.getNickname(),
                    serviceAccount.getUserType(),
                    "ONLINE",
                    LocalDateTime.now().plusHours(1),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    UUID_GENERATOR.generate(),
                    serviceAccountPublicId,
                    null,
                    null,
                    null));
            assertThat(authService.contextOptions()).isEmpty();

            assertBizCode(() -> bindings.get(alphaBindingPublicId), "IAM_USER_PERSON_BINDING_NOT_FOUND");
            assertBizCode(
                    () -> bindings.update(
                            alphaBindingPublicId,
                            new UserPersonBindingModels.UpdateRequest(
                                    serviceAccountPublicId,
                                    alphaPersonPublicId,
                                    START,
                                    null,
                                    null,
                                    0)),
                    "IAM_USER_PERSON_BINDING_NOT_FOUND");
            assertBizCode(
                    () -> bindingDirectory.findEffectiveByUser(alphaUserPublicId, Instant.now()),
                    "IAM_USER_NOT_FOUND");
            assertBizCode(
                    () -> bindings.list(1, 20, null, alphaPersonPublicId, null, null),
                    "ORG_PERSON_NOT_FOUND");
            assertBizCode(
                    () -> bindings.create(new UserPersonBindingModels.CreateRequest(
                            serviceAccountPublicId, alphaPersonPublicId, START, null, null)),
                    "ORG_PERSON_NOT_FOUND");
            assertThat(bindingDirectory.findEffectiveByUser(serviceAccountPublicId, Instant.now())).isEmpty();
        }
    }

    @Test
    void preventsOneUserFromSelectingAnotherUsersAssignment() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_CONTEXT, "context", 44L)) {
            StructureFixture fixture = activeStructure("context-org");
            PersonModels.Response personA = activePerson("person-a");
            PersonModels.Response personB = activePerson("person-b");
            AssignmentModels.Response assignmentA = activeAssignment("engagement-a", personA, fixture);
            AssignmentModels.Response assignmentB = activeAssignment("engagement-b", personB, fixture);
            UserEntity userA = createUser("user-a", "STAFF");
            UserEntity userB = createUser("user-b", "STAFF");
            UserPersonBindingModels.Response bindingA = activateBinding(userA, personA, START, null);
            activateBinding(userB, personB, START, null);

            AuthContext.set(new AuthPrincipal(
                    98_765L,
                    TENANT_CONTEXT,
                    "context",
                    userA.getId(),
                    userA.getUsername(),
                    userA.getNickname(),
                    userA.getUserType(),
                    "ONLINE",
                    LocalDateTime.now().plusHours(1),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    UUID_GENERATOR.generate(),
                    UUID.fromString(userA.getPublicId()),
                    bindingA.publicId(),
                    personA.publicId(),
                    null));

            assertThat(assignmentA.publicId()).isNotEqualTo(assignmentB.publicId());
            assertBizCode(
                    () -> authService.selectContext(new AuthContextSelectionRequest(assignmentB.publicId())),
                    "ORG_ASSIGNMENT_PERSON_MISMATCH");
        }
    }

    @Test
    void requiresBindingPeriodToBeContainedWithinPersonPeriod() {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(
                TENANT_BINDINGS, "bindings", 41L)) {
            Instant personFrom = Instant.parse("2025-01-01T00:00:00Z");
            Instant personTo = Instant.parse("2030-01-01T00:00:00Z");
            PersonModels.Response person = activePerson("bounded-person", personFrom, personTo);
            UserEntity user = createUser("bounded-user", "STAFF");

            UserPersonBindingModels.Response startsTooEarly = bindings.create(
                    new UserPersonBindingModels.CreateRequest(
                            UUID.fromString(user.getPublicId()), person.publicId(),
                            Instant.parse("2024-01-01T00:00:00Z"),
                            Instant.parse("2026-01-01T00:00:00Z"), null));
            assertBizCode(
                    () -> bindings.transition(
                            startsTooEarly.publicId(),
                            new UserPersonBindingModels.TransitionRequest(
                                    UserPersonBindingModels.Status.ACTIVE, startsTooEarly.version())),
                    "IAM_USER_PERSON_PERIOD_OUTSIDE_PERSON");

            UserPersonBindingModels.Response unbounded = bindings.create(
                    new UserPersonBindingModels.CreateRequest(
                            UUID.fromString(user.getPublicId()), person.publicId(),
                            Instant.parse("2026-01-01T00:00:00Z"), null, null));
            assertBizCode(
                    () -> bindings.transition(
                            unbounded.publicId(),
                            new UserPersonBindingModels.TransitionRequest(
                                    UserPersonBindingModels.Status.ACTIVE, unbounded.version())),
                    "IAM_USER_PERSON_PERIOD_OUTSIDE_PERSON");

            UserPersonBindingModels.Response contained = activateBinding(
                    user, person,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2029-01-01T00:00:00Z"));
            assertThat(contained.status()).isEqualTo(UserPersonBindingModels.Status.ACTIVE);
        }
    }

    private UserEntity createUser(String code, String userType) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserEntity user = new UserEntity();
        user.setPublicId(UUID_GENERATOR.generate().toString());
        user.setUserCode(code + "-" + suffix);
        user.setUsername(code + "." + suffix);
        user.setNickname(code);
        user.setEmployeeId(0L);
        user.setUserType(userType);
        user.setStatus("ENABLED");
        user.setPasswordHash("test-only");
        user.setNeedResetPassword(false);
        user.setVersion(0);
        user.setDeleted(0);
        assertThat(userMapper.insert(user)).isEqualTo(1);
        return user;
    }

    private PersonModels.Response activePerson(String code) {
        return activePerson(code, START, null);
    }

    private PersonModels.Response activePerson(String code, Instant validFrom, Instant validTo) {
        PersonModels.Response person = workforce.createPerson(
                new PersonModels.CreateRequest(code, code, validFrom, validTo, null));
        return workforce.transitionPerson(
                person.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, person.version()));
    }

    private UserPersonBindingModels.Response activateBinding(UserEntity user,
                                                              PersonModels.Response person,
                                                              Instant validFrom,
                                                              Instant validTo) {
        UserPersonBindingModels.Response binding = bindings.create(
                new UserPersonBindingModels.CreateRequest(
                        UUID.fromString(user.getPublicId()), person.publicId(), validFrom, validTo, null));
        return bindings.transition(
                binding.publicId(),
                new UserPersonBindingModels.TransitionRequest(
                        UserPersonBindingModels.Status.ACTIVE, binding.version()));
    }

    private StructureFixture activeStructure(String code) {
        OrganizationModels.Response organization = catalog.createOrganization(
                new OrganizationModels.CreateRequest(code, code, null, START, null, null));
        organization = catalog.transitionOrganization(
                organization.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, organization.version()));
        OrgUnitModels.Response unit = structure.createUnit(
                new OrgUnitModels.CreateRequest(
                        organization.publicId(), null, code + "-unit", code + " unit", 0, START, null, null));
        unit = structure.transitionUnit(
                unit.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));
        PositionModels.Response position = structure.createPosition(
                new PositionModels.CreateRequest(
                        organization.publicId(), unit.publicId(), code + "-position", code + " position", START, null, null));
        position = structure.transitionPosition(
                position.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, position.version()));
        return new StructureFixture(organization, unit, position);
    }

    private AssignmentModels.Response activeAssignment(String code,
                                                        PersonModels.Response person,
                                                        StructureFixture fixture) {
        EngagementModels.Response engagement = workforce.createEngagement(
                new EngagementModels.CreateRequest(
                        code,
                        person.publicId(),
                        fixture.organization().publicId(),
                        new TypeDescriptorInput("urn:saas-basics:organization", "MEMBER_OF", "1.0.0"),
                        START,
                        null,
                        null));
        engagement = workforce.transitionEngagement(
                engagement.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, engagement.version()));
        AssignmentModels.Response assignment = workforce.createAssignment(
                new AssignmentModels.CreateRequest(
                        engagement.publicId(), fixture.unit().publicId(), fixture.position().publicId(), true,
                        START, null, null));
        return workforce.transitionAssignment(
                assignment.publicId(), new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, assignment.version()));
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
