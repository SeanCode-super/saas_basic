package com.saasbasics.platform.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
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
import com.saasbasics.platform.modules.tenant.dto.TenantSaveRequest;
import com.saasbasics.platform.modules.tenant.service.TenantMeteringService;
import com.saasbasics.platform.modules.tenant.service.TenantService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@Sql(statements = """
        INSERT INTO plat_tenant_quota (tenant_id, quota_code, quota_name, quota_value)
        VALUES (6101, 'DEPT_COUNT', 'Departments', 200);
        """, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(classes = SaasBasicsApplication.class)
class EnterpriseScaleAndResilienceIntegrationTest {

    private static final long TENANT_ID = 6101L;
    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

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

    @Autowired private OrganizationCatalogService catalog;
    @Autowired private OrganizationStructureService structure;
    @Autowired private OrganizationWorkforceService workforce;
    @Autowired private OrganizationDirectory directory;
    @Autowired private TenantService tenantService;
    @Autowired private TenantMeteringService meteringService;

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        TenantAccessContextHolder.clear();
    }

    @Test
    void handlesDeepOrgTreeMovesConcurrentPrimaryAssignmentContentionAndBackupRestoreRoundtrip() throws Exception {
        // 1. Deep 25-level org tree move + cycle guard
        try (var ignored = TenantAccessContextHolder.openTenant(TENANT_ID, "scale6101", 1L)) {
            var org = catalog.createOrganization(new OrganizationModels.CreateRequest(
                    "scale-org", "Scale Org", null, START, null, null));
            org = catalog.transitionOrganization(org.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, org.version()));

            List<OrgUnitModels.Response> chain = new ArrayList<>();
            UUID parentId = null;
            for (int depth = 0; depth < 25; depth++) {
                var unit = structure.createUnit(new OrgUnitModels.CreateRequest(
                        org.publicId(), parentId, "u-" + depth, "Unit " + depth, depth, START, null, null));
                unit = structure.transitionUnit(unit.publicId(),
                        new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, unit.version()));
                chain.add(unit);
                parentId = unit.publicId();
            }

            OrgUnitModels.Response anchor = structure.createUnit(new OrgUnitModels.CreateRequest(
                    org.publicId(), chain.get(0).publicId(), "u-anchor", "Anchor", 99, START, null, null));
            anchor = structure.transitionUnit(anchor.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, anchor.version()));

            // Moving node[15] onto anchor should transactionally update subtree node[15..24] (10 units)
            var node15 = chain.get(15);
            structure.moveUnit(node15.publicId(), new OrgUnitModels.MoveRequest(anchor.publicId(), node15.version()));
            assertThat(directory.expandOrgUnitSubtree(node15.publicId())).hasSize(10);
            assertThat(directory.expandOrgUnitSubtree(anchor.publicId())).hasSize(11);

            // Moving anchor below its new descendant node[24] must throw ORG_UNIT_CYCLE
            var refreshedAnchor = structure.getUnit(anchor.publicId());
            var node24 = chain.get(24);
            assertThatThrownBy(() -> structure.moveUnit(refreshedAnchor.publicId(),
                    new OrgUnitModels.MoveRequest(node24.publicId(), refreshedAnchor.version())))
                    .isInstanceOf(BizException.class)
                    .extracting("code").isEqualTo("ORG_UNIT_CYCLE");

            // 2. Concurrent primary assignment contention (6 threads racing to create active primary assignments)
            var person = workforce.createPerson(new PersonModels.CreateRequest("p-race", "Race Person", START, null, null));
            person = workforce.transitionPerson(person.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, person.version()));
            var eng = workforce.createEngagement(new EngagementModels.CreateRequest(
                    "eng-race", person.publicId(), org.publicId(),
                    new TypeDescriptorInput("urn:saas-basics:organization:engagement-type", "employment", "1.0.0"),
                    START, null, null));
            eng = workforce.transitionEngagement(eng.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, eng.version()));
            var pos = structure.createPosition(new PositionModels.CreateRequest(
                    org.publicId(), anchor.publicId(), "pos-race", "Race Pos", START, null, null));
            pos = structure.transitionPosition(pos.publicId(),
                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, pos.version()));

            final UUID engId = eng.publicId();
            final UUID unitId = anchor.publicId();
            final UUID posId = pos.publicId();
            CyclicBarrier barrier = new CyclicBarrier(6);
            try (var pool = Executors.newFixedThreadPool(6)) {
                List<Callable<Boolean>> tasks = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    tasks.add(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        try (var s = TenantAccessContextHolder.openTenant(TENANT_ID, "scale6101", 1L)) {
                            var a = workforce.createAssignment(new AssignmentModels.CreateRequest(
                                    engId, unitId, posId, Boolean.TRUE, START, null, null));
                            workforce.transitionAssignment(a.publicId(),
                                    new LifecycleTransitionRequest(LifecycleStatus.ACTIVE, a.version()));
                            return true;
                        } catch (Exception ex) {
                            return false;
                        }
                    });
                }
                List<Future<Boolean>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
                long winners = futures.stream().filter(f -> {
                    try { return Boolean.TRUE.equals(f.get()); } catch (Exception e) { return false; }
                }).count();
                assertThat(winners).isEqualTo(1L);
            }
        }

        // 3. Tenant backup snapshot & zip checksum-verified restore roundtrip
        var platformAdmin = new AuthPrincipal(1L, 1L, "platform", 1L, "admin", "Platform Admin",
                "PLATFORM", "ONLINE", null, List.of("*"), List.of(), List.of(1L), List.of());
        AuthContext.set(platformAdmin);
        try (var ignored = TenantAccessContextHolder.openAuthenticated(platformAdmin)) {
            var created = tenantService.createTenant(new TenantSaveRequest(
                    "snap_6102", "Snapshot Tenant 6102", 1L, "ACTIVE", "SHARED_SCHEMA",
                    null, "Ops", "13900006102", "ops@example.com", null));
            long snapTenantId = created.id();

            var snap = meteringService.generateTenantSnapshot(snapTenantId);
            assertThat(snap.checksumSha256()).hasSize(64);
            assertThat(snap.archiveSizeBytes()).isPositive();

            Resource zipResource = meteringService.snapshotResource(snap.snapshotId());
            byte[] zipBytes = zipResource.getInputStream().readAllBytes();
            var multipart = new MockMultipartFile("file", snap.snapshotId() + ".zip", "application/zip", zipBytes);
            var restored = meteringService.restoreTenantSnapshot(multipart, snapTenantId, true);
            assertThat(restored.snapshotId()).isEqualTo(snap.snapshotId());
            assertThat(restored.restoredCounts()).isNotEmpty();
        }
    }
}
