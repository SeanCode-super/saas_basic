package com.saasbasics.platform.modules.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataPermissionProvider;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.modules.audit.dto.BlacklistCreateRequest;
import com.saasbasics.platform.modules.audit.dto.HighRiskActionItemResponse;
import com.saasbasics.platform.modules.audit.mapper.AuditQueryMapper;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import com.saasbasics.platform.modules.audit.mapper.SecBlacklistMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuditTenantIsolationTest {

    private JdbcTemplate jdbc;
    private Connection keeper;
    private AuditService service;

    @BeforeEach
    void configureDatabaseAndMappers() throws Exception {
        var data = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        keeper = data.getConnection();
        jdbc = new JdbcTemplate(data);
        jdbc.execute("""
                CREATE TABLE sec_blacklist (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, black_type VARCHAR(32),
                  black_value VARCHAR(255), reason VARCHAR(500), expired_at TIMESTAMP, status VARCHAR(32),
                  deleted INT DEFAULT 0, version INT DEFAULT 0, created_by BIGINT, updated_by BIGINT,
                  created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE sys_config (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT, config_key VARCHAR(64),
                  config_group VARCHAR(32), config_value VARCHAR(256), status VARCHAR(32), deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE iam_api_resource (
                  id BIGINT PRIMARY KEY, tenant_id BIGINT, resource_code VARCHAR(64), resource_name VARCHAR(64),
                  http_method VARCHAR(16), url_pattern VARCHAR(100), status VARCHAR(32), deleted INT)
                """);

        var beans = new StaticListableBeanFactory();
        var configuration = new MybatisConfiguration();
        configuration.addMapper(SecBlacklistMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(data);
        factory.setMapperLocations(new ClassPathResource("mapper/AuditQueryMapper.xml"));
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(
                new EnterpriseDataPermissionHandler(beans.getBeanProvider(DataPermissionProvider.class))));
        var template = new SqlSessionTemplate(factory.getObject());
        beans.addBean("auditQueries", template.getMapper(AuditQueryMapper.class));
        beans.addBean("blacklist", template.getMapper(SecBlacklistMapper.class));
        service = new AuditService(beans.getBeanProvider(LoginLogMapper.class),
                beans.getBeanProvider(OperationLogMapper.class), beans.getBeanProvider(RiskRuleMapper.class),
                beans.getBeanProvider(SecBlacklistMapper.class), beans.getBeanProvider(AuditQueryMapper.class),
                beans.getBeanProvider(AuditTrailService.class));
    }

    @AfterEach
    void closeDatabase() throws Exception {
        keeper.close();
    }

    @Test
    void alternatesTenantsAndInvalidatesPrivateAndPublicRulesAfterWrites() {
        blacklist(0, "IP", "shared-legacy");
        blacklist(1, "USER", "shared-platform");
        blacklist(22, "IP", "tenant-22");
        blacklist(33, "IP", "tenant-33");

        for (long id : new long[]{22, 33, 22, 33}) {
            try (var scope = tenant(id)) {
                blocked(id, "guest", "tenant-" + id);
                service.assertAllowed(id, "guest", id == 22 ? "tenant-33" : "tenant-22");
                blocked(id, "guest", "shared-legacy");
                blocked(id, "Shared-Platform", "localhost");
            }
        }
        try (var scope = tenant(22)) {
            var row = service.addBlacklist(new BlacklistCreateRequest("IP", "new-private", "tenant 22", null));
            blocked(22, "guest", "new-private");
            service.removeBlacklist(row.id());
            service.assertAllowed(22L, "guest", "new-private");
        }
        try (var scope = tenant(1)) {
            service.addBlacklist(new BlacklistCreateRequest("IP", "new-public", "platform", null));
        }
        try (var scope = tenant(33)) {
            blocked(33, "guest", "new-public");
        }
    }

    @Test
    void honorsCurrentAndLegacyPolicyKeysWithoutDisablingPlatformRulesFromAnotherTenant() {
        blacklist(1, "IP", "public");
        blacklist(22, "IP", "local");
        blacklist(33, "IP", "local");
        policy(22, "audit.policy", true);
        policy(22, "audit.risk_policy", false);
        policy(33, "audit.policy", true);
        try (var scope = tenant(22)) {
            service.assertAllowed(22L, "guest", "local");
            blocked(22, "guest", "public");
            assertThatThrownBy(() -> service.assertAllowed(33L, "guest", "local"))
                    .isInstanceOfSatisfying(BizException.class,
                            error -> assertThat(error.getCode()).isEqualTo("TENANT_SCOPE_MISMATCH"));
        }
        try (var scope = tenant(33)) {
            blocked(33, "guest", "local");
            blocked(33, "guest", "public");
        }
    }

    @Test
    void ignoresDeletedDisabledAndExpiredEntries() {
        blacklist(22, "IP", "expired");
        blacklist(22, "IP", "deleted");
        blacklist(1, "IP", "disabled");
        jdbc.update("UPDATE sec_blacklist SET expired_at = TIMESTAMP '2000-01-01 00:00:00' WHERE black_value='expired'");
        jdbc.update("UPDATE sec_blacklist SET deleted = 1 WHERE black_value='deleted'");
        jdbc.update("UPDATE sec_blacklist SET status = 'DISABLED' WHERE black_value='disabled'");
        try (var scope = tenant(22)) {
            service.assertAllowed(22L, "guest", "expired");
            service.assertAllowed(22L, "guest", "deleted");
            service.assertAllowed(22L, "guest", "disabled");
        }
    }

    @Test
    void enforcesTenantRulesRegardlessOfTheCurrentBusinessPagesRowPermissions() {
        blacklist(22, "IP", "local");
        try (var scope = tenant(22)) {
            DataPermissionContext.setSqlSpec(DataPermissionSqlSpec.denyAll(22L));
            try {
                blocked(22, "guest", "local");
            } finally {
                DataPermissionContext.clear();
            }
        }
    }

    @Test
    void disabledPlatformEngineStopsBothPublicAndPrivateEnforcement() {
        policy(1, "audit.risk_policy", false);
        policy(22, "audit.risk_policy", true);
        blacklist(1, "IP", "public");
        blacklist(22, "IP", "local");
        try (var scope = tenant(22)) {
            service.assertAllowed(22L, "guest", "public");
            service.assertAllowed(22L, "guest", "local");
        }
    }

    @Test
    void prefersOwnCatalogAndReadsOnlyTheFixedPublicCatalogWhenEmpty() {
        jdbc.update("""
                INSERT INTO iam_api_resource VALUES
                (1, 1, 'public', 'Public', 'POST', '/public','ENABLED',0),
                (2, 1, 'public-read', 'Read', 'GET', '/read','ENABLED',0),
                (3, 22, 'tenant-22', 'Local', 'POST', '/22','ENABLED',0),
                (4, 33, 'hidden', 'Disabled', 'POST', '/33','DISABLED',0)
                """);
        try (var scope = tenant(22)) {
            assertThat(service.listHighRiskCatalog()).extracting(HighRiskActionItemResponse::code)
                    .containsExactly("tenant-22");
        }
        try (var scope = tenant(33)) {
            assertThat(service.listHighRiskCatalog()).extracting(HighRiskActionItemResponse::code)
                    .containsExactly("public");
            assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(33L);
        }
    }

    private TenantAccessContextHolder.Scope tenant(long id) {
        return TenantAccessContextHolder.openTenant(id, "tenant-" + id, 7L);
    }

    private void blacklist(long id, String type, String target) {
        jdbc.update("INSERT INTO sec_blacklist (tenant_id,black_type,black_value,status) VALUES (?,?,?,'ENABLED')",
                id, type, target);
    }

    private void policy(long id, String key, boolean enabled) {
        jdbc.update("""
                INSERT INTO sys_config (tenant_id,config_group,config_key,config_value,status)
                VALUES (?,'AUDIT',?,?,'ENABLED')
                """, id, key, "{\"blacklistEnabled\":" + enabled + "}");
    }

    private void blocked(long id, String username, String ip) {
        assertThatThrownBy(() -> service.assertAllowed(id, username, ip))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SECURITY_BLACKLIST_BLOCKED"));
    }
}
