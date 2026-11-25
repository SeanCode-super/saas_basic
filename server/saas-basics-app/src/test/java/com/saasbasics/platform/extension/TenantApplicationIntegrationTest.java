package com.saasbasics.platform.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.SaasBasicsApplication;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantAppAccess;
import com.saasbasics.platform.common.tenant.TenantApplication;
import com.saasbasics.platform.modules.tenant.dto.TenantAppSubscribeRequest;
import com.saasbasics.platform.modules.tenant.service.TenantAppService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {SaasBasicsApplication.class, TenantApplicationIntegrationTest.CourseModule.class})
@Testcontainers(disabledWithoutDocker = true)
class TenantApplicationIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @TestConfiguration
    static class CourseModule {
        @Bean
        TenantApplication education(JdbcTemplate jdbc) {
            return new TenantApplication() {
                public String code() { return "education"; }
                public String name() { return "课程"; }
                public String description() { return "教学计划"; }
                public String componentPath() { return "@/views/education/CourseView.vue"; }
                public List<Endpoint> endpoints() { return List.of(new Endpoint("query", "课程查询", "GET", "/api/apps/education/**")); }
                public List<WorkspaceField> workspaceFields(int revision) {
                    var title = new WorkspaceField("title", "课程", "text", true);
                    return revision == 1 ? List.of(title) : List.of(title, new WorkspaceField("credits", "学分", "number", false));
                }
                public int latestRevision() { return 2; }
                public void migrate(long tenantId, int revision) {
                    assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(tenantId);
                    if (revision == 2) {
                        jdbc.update("UPDATE fixture_course SET credits = 3 WHERE tenant_id = ?", tenantId);
                        if (tenantId == 202) throw new IllegalStateException("fixture migration failed");
                    }
                }
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantAppService apps;
    @Autowired TenantAppAccess access;
    @Autowired com.saasbasics.platform.modules.tenant.service.IndustryWorkspace workspace;

    private TenantAccessContextHolder.Scope login(long tenantId, String tenantCode) {
        var actor = new AuthPrincipal(tenantId, tenantId, tenantCode, tenantId, tenantCode + "_admin", "管理员",
                "TENANT", "ONLINE", LocalDateTime.now().plusHours(1), List.of("tenant:write", "biz_education:query"),
                List.of(), List.of(), List.of());
        AuthContext.set(actor);
        return TenantAccessContextHolder.openAuthenticated(actor);
    }

    @AfterEach
    void clearPrincipal() {
        AuthContext.clear();
    }

    @Test
    void upgradeIsTenantScopedTransactionalAndRepeatable() {
        jdbc.execute("CREATE TABLE fixture_course (tenant_id BIGINT PRIMARY KEY, title VARCHAR(64), credits INT)");
        jdbc.update("INSERT INTO plat_tenant (id, tenant_code, tenant_name) VALUES (201,'alpha','Alpha'), (202,'beta','Beta')");
        for (long id : List.of(201L, 202L)) {
            try (var scope = login(id, id == 201 ? "alpha" : "beta")) {
                assertThat(access.isInstalled(id, "education", 1)).isFalse();
                apps.subscribeApp(id, new TenantAppSubscribeRequest("education", null, null, 1));
                jdbc.update("INSERT INTO fixture_course VALUES (?, 'Math', NULL)", id);
            }
        }
        try (var scope = login(201L, "alpha")) {
            assertThatThrownBy(() -> apps.upgrade(202, "education", 2))
                    .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo("TENANT_ACCESS_DENIED"));
            assertThat(jdbc.queryForObject("SELECT credits FROM fixture_course WHERE tenant_id=202", Integer.class)).isNull();
            apps.upgrade(201, "education", 2);
            apps.upgrade(201, "education", 2);
            assertThat(workspace.entries("mobile")).singleElement().satisfies(e -> {
                assertThat(e.kind()).isEqualTo("MODULE");
                assertThat(e.columns()).hasSize(2);
            });
            assertThat(workspace.entries("pc")).isEqualTo(workspace.entries("mobile"));
            apps.resetToPackageBaseline(201L);
            assertThat(workspace.entries("mobile")).isEmpty();
            assertThat(access.isInstalled(201, "education", 1)).isFalse();
            apps.subscribeApp(201L, new TenantAppSubscribeRequest("education", null, null));
            assertThat(access.isInstalled(201, "education", 2)).isTrue();
            assertThat(jdbc.queryForObject("SELECT title FROM fixture_course WHERE tenant_id=201", String.class)).isEqualTo("Math");
        }
        try (var scope = login(202L, "beta")) {
            assertThatThrownBy(() -> apps.upgrade(202, "education", 2)).hasMessageContaining("fixture migration failed");
            assertThat(access.isInstalled(202, "education", 1)).isTrue();
            assertThat(access.isInstalled(202, "education", 2)).isFalse();
            assertThat(workspace.entries("mobile")).singleElement().satisfies(e -> assertThat(e.columns()).hasSize(1));
            assertThat(jdbc.queryForObject("SELECT credits FROM fixture_course WHERE tenant_id=202", Integer.class)).isNull();
        }
    }
}
