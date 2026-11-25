package com.saasbasics.platform.modules.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.config.MybatisPlusConfig;
import com.saasbasics.platform.datasource.DynamicRoutingDataSource;
import com.saasbasics.platform.datasource.TenantDataSourceRegistry;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaItemResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import com.saasbasics.platform.security.EnterpriseDataPermissionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantQuotaServiceTest {

    private final TenantMapper tenants = mock(TenantMapper.class);
    private TenantQuotaService service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource shared = database();
        JdbcDataSource dedicated = database();
        JdbcTemplate central = new JdbcTemplate(shared);
        central.execute("""
                CREATE TABLE plat_tenant_quota (id BIGINT PRIMARY KEY, tenant_id BIGINT, quota_code VARCHAR,
                    quota_name VARCHAR, quota_value BIGINT, used_value BIGINT, alarm_threshold BIGINT,
                    reset_cycle VARCHAR, created_by BIGINT, created_at TIMESTAMP, updated_by BIGINT,
                    updated_at TIMESTAMP, deleted INT, deleted_at TIMESTAMP, version INT, remark VARCHAR)
                """);
        central.update("""
                INSERT INTO plat_tenant_quota (id, tenant_id, quota_code, quota_value, used_value, deleted)
                VALUES (1, 2, 'USER_COUNT', 10, 42, 0), (2, 2, 'DEPT_COUNT', 10, 42, 0),
                       (3, 2, 'STORAGE_MB', 2, 0, 0), (4, 2, 'API_PER_MINUTE', 100, 12, 0),
                       (5, 3, 'STORAGE_MB', 4, 0, 0), (6, 2, 'OLD_QUOTA', 1, 0, 1)
                """);
        JdbcTemplate isolated = new JdbcTemplate(dedicated);
        isolated.update("INSERT INTO iam_user VALUES (2,0),(2,0),(2,1),(3,0)");
        isolated.update("INSERT INTO iam_department VALUES (2,0),(2,1),(3,0)");
        isolated.update("INSERT INTO file_object VALUES (2,0,1572864),(2,1,5000000),(3,0,1000000)");
        central.update("INSERT INTO file_object VALUES (3,0,1048576),(3,1,2000000),(2,0,2000000)");

        TenantDataSourceRegistry registry = new TenantDataSourceRegistry();
        registry.registerDataSource(2L, dedicated);
        DynamicRoutingDataSource routing = new DynamicRoutingDataSource(registry);
        routing.setTargetDataSources(Map.of(DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY, shared, 2L, dedicated));
        routing.setDefaultTargetDataSource(shared);
        routing.afterPropertiesSet();
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(routing);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor(new EnterpriseDataPermissionHandler(null)));
        factory.setMapperLocations(new ClassPathResource("mapper/TenantQuotaMapper.xml"),
                new ClassPathResource("mapper/PlatTenantMeteringMapper.xml"));
        SqlSessionTemplate sql = new SqlSessionTemplate(factory.getObject());
        service = new TenantQuotaService(sql.getMapper(TenantQuotaMapper.class), mock(TenantPackageMapper.class),
                tenants, sql.getMapper(PlatTenantMeteringMapper.class));
        for (long id : List.of(2L, 3L)) {
            TenantEntity tenant = new TenantEntity();
            tenant.setId(id);
            tenant.setTenantCode("t" + id);
            tenant.setPackageId(10L);
            tenant.setExpireAt(LocalDateTime.of(2039, 11, 30, 0, 0));
            when(tenants.selectById(id)).thenReturn(tenant);
        }
    }

    private static JdbcDataSource database() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE iam_user (tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE org_unit (tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE iam_department (tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE file_object (tenant_id BIGINT, deleted INT, file_size BIGINT)");
        jdbc.execute("CREATE VIEW plat_tenant_storage_sources AS SELECT tenant_id, file_size FROM file_object WHERE deleted=0");
        return ds;
    }

    private static TenantAccessContextHolder.Scope signIn(long id, String type, List<String> permissions) {
        AuthPrincipal principal = new AuthPrincipal(1L, id, "t" + id, 77L, "operator", "Operator", type,
                "ACTIVE", null, permissions, List.of(), List.of(), List.of());
        AuthContext.set(principal);
        return TenantAccessContextHolder.openAuthenticated(principal);
    }

    @AfterEach
    void clearPrincipal() {
        AuthContext.clear();
    }

    @Test
    void platformReadsTargetConfigurationAndCountsOnlyItsLiveRowsOnItsOwnDataSource() {
        try (var scope = signIn(1, "PLATFORM", List.of("tenant:query"))) {
            var overview = service.getTenantQuotaOverview(2L);
            assertThat(overview.tenantId()).isEqualTo(2L);
            assertThat(overview.expireAt()).isEqualTo("2039-11-30 00:00:00");
            assertThat(overview.quotas()).extracting(TenantQuotaItemResponse::usedValue)
                    .containsExactly(2L, 1L, 1L, 12L);
            assertThat(overview.quotas().get(2).usagePercent()).isEqualTo(75.0);
            assertThat(TenantAccessContextHolder.requiredTenantId()).isEqualTo(1L);
        }
    }

    @Test
    void tenantAndPlatformOperatorWithoutPermissionCannotReadAnotherTenantsConfiguration() {
        try (var scope = signIn(3, "TENANT", List.of("tenant:query"))) {
            assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.getTenantQuotaOverview(2L))
                    .extracting(BizException::getCode).isEqualTo("TENANT_BYPASS_FORBIDDEN");
        }
        try (var scope = signIn(1, "PLATFORM", List.of("iam:user:query"))) {
            assertThatExceptionOfType(BizException.class).isThrownBy(() -> service.getTenantQuotaOverview(2L))
                    .extracting(BizException::getCode).isEqualTo("TENANT_BYPASS_FORBIDDEN");
        }
        verifyNoInteractions(tenants);
    }

    @Test
    void tenantReadsOwnConsumptionAndStorageCheckHonoursTheExactByteBoundary() {
        try (var scope = signIn(3, "TENANT", List.of("tenant:query"))) {
            var item = service.getTenantQuotaOverview(3L).quotas().getFirst();
            assertThat(item.usedValue()).isEqualTo(1L);
            assertThat(item.usagePercent()).isEqualTo(25.0);
            service.checkStorageQuota(3L, 3L * 1024 * 1024);
            assertThatExceptionOfType(BizException.class)
                    .isThrownBy(() -> service.checkStorageQuota(3L, 3L * 1024 * 1024 + 1))
                    .extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
            assertThatExceptionOfType(BizException.class)
                    .isThrownBy(() -> service.checkStorageQuota(3L, Long.MAX_VALUE))
                    .extracting(BizException::getCode).isEqualTo("TENANT_STORAGE_QUOTA_EXCEEDED");
        }
    }
}
