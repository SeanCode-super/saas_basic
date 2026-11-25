package com.saasbasics.platform.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.DynamicDataSourceConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = DynamicDataSourceRoutingIntegrationTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:shared_routing_db;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "saas-basics.datasource.dedicated.888.url=jdbc:h2:mem:vip_888_db;DB_CLOSE_DELAY=-1",
                "saas-basics.datasource.dedicated.888.username=sa",
                "saas-basics.datasource.dedicated.888.password=",
                "saas-basics.datasource.dedicated.888.driver-class-name=org.h2.Driver"
        }
)
@DisplayName("动态多数据源运行时 JDBC 真实路由集成测试")
class DynamicDataSourceRoutingIntegrationTest {

    @Configuration
    @Import({DynamicDataSourceConfig.class, TenantDataSourceRegistry.class})
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
    static class TestConfig {
    }

    @Autowired
    private DataSource primaryDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantDataSourceRegistry registry;

    @AfterEach
    void clearContext() {
        TenantAccessContextHolder.clear();
    }

    @Test
    void routesLiveSqlConnectionsBetweenSharedAndDedicatedPhysicalDatabases() {
        assertThat(primaryDataSource).isInstanceOf(DynamicRoutingDataSource.class);

        // Initialize marker table in shared DB
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS db_marker (source_name VARCHAR(64))");
        jdbcTemplate.execute("DELETE FROM db_marker");
        jdbcTemplate.update("INSERT INTO db_marker (source_name) VALUES ('shared-default')");

        // Initialize marker table in tenant 888 dedicated DB
        try (var ignored = TenantAccessContextHolder.openTenant(888L, "vip888", 1L)) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS db_marker (source_name VARCHAR(64))");
            jdbcTemplate.execute("DELETE FROM db_marker");
            jdbcTemplate.update("INSERT INTO db_marker (source_name) VALUES ('vip-888-dedicated')");
        }

        // Shared tenant 100 reads shared DB
        try (var ignored = TenantAccessContextHolder.openTenant(100L, "std100", 1L)) {
            assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM db_marker", String.class))
                    .isEqualTo("shared-default");
        }

        // VIP tenant 888 reads dedicated DB
        try (var ignored = TenantAccessContextHolder.openTenant(888L, "vip888", 1L)) {
            assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM db_marker", String.class))
                    .isEqualTo("vip-888-dedicated");
        }

        // System bypass falls back to shared default DB
        try (var ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE, "test")) {
            assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM db_marker", String.class))
                    .isEqualTo("shared-default");
        }

        // Live runtime registration and unregistration of tenant 999 dedicated DB
        DataSource live999 = DataSourceBuilder.create()
                .url("jdbc:h2:mem:vip_999_db;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        registry.registerDataSource(999L, live999);
        try (var ignored = TenantAccessContextHolder.openTenant(999L, "vip999", 1L)) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS db_marker (source_name VARCHAR(64))");
            jdbcTemplate.execute("DELETE FROM db_marker");
            jdbcTemplate.update("INSERT INTO db_marker (source_name) VALUES ('vip-999-live')");
            assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM db_marker", String.class))
                    .isEqualTo("vip-999-live");
        }

        registry.unregisterDataSource(999L);
        try (var ignored = TenantAccessContextHolder.openTenant(999L, "vip999", 1L)) {
            assertThat(jdbcTemplate.queryForObject("SELECT source_name FROM db_marker", String.class))
                    .isEqualTo("shared-default");
        }
    }
}
