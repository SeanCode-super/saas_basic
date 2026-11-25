package com.saasbasics.platform.datasource;

import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("动态多数据源路由与租户物理隔离测试")
class DynamicRoutingDataSourceTest {

    private TenantDataSourceRegistry registry;
    private DynamicRoutingDataSource routingDataSource;
    private DataSource defaultDataSource;
    private DataSource dedicatedDataSource;

    @BeforeEach
    void setUp() {
        registry = new TenantDataSourceRegistry();
        routingDataSource = new DynamicRoutingDataSource(registry);
        defaultDataSource = Mockito.mock(DataSource.class);
        dedicatedDataSource = Mockito.mock(DataSource.class);

        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.setTargetDataSources(Map.of(
                DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY, defaultDataSource,
                100L, dedicatedDataSource
        ));
        routingDataSource.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        TenantAccessContextHolder.clear();
    }

    @Test
    @DisplayName("无租户上下文时默认路由至共享主库")
    void shouldRouteToDefaultWhenNoTenantContext() throws Exception {
        TenantAccessContextHolder.clear();
        Object key = invokeDetermineCurrentLookupKey();
        assertThat(key).isEqualTo(DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY);
    }

    @Test
    @DisplayName("系统绕过租户隔离标记时必须路由至默认主库")
    void shouldRouteToDefaultWhenBypassIsolation() throws Exception {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE, "test")) {
            Object key = invokeDetermineCurrentLookupKey();
            assertThat(key).isEqualTo(DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY);
        }
    }

    @Test
    @DisplayName("普通租户未配置独立物理库时自动回退到共享默认库")
    void shouldRouteToDefaultWhenTenantHasNoDedicatedDatabase() throws Exception {
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(999L, "shared-tenant", 1L)) {
            Object key = invokeDetermineCurrentLookupKey();
            assertThat(key).isEqualTo(DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY);
        }
    }

    @Test
    @DisplayName("战略VIP租户配置独立物理库时精准动态切换至租户专有库")
    void shouldRouteToDedicatedDatabaseForVipTenant() throws Exception {
        registry.registerDataSource(100L, dedicatedDataSource);
        assertThat(registry.hasDedicatedDataSource(100L)).isTrue();

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(100L, "vip-tenant", 1L)) {
            Object key = invokeDetermineCurrentLookupKey();
            assertThat(key).isEqualTo(100L);
        }

        // 卸载专用库后应平滑退回共享库
        registry.unregisterDataSource(100L);
        assertThat(registry.hasDedicatedDataSource(100L)).isFalse();

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openTenant(100L, "vip-tenant", 1L)) {
            Object key = invokeDetermineCurrentLookupKey();
            assertThat(key).isEqualTo(DynamicRoutingDataSource.DEFAULT_DATASOURCE_KEY);
        }
    }

    private Object invokeDetermineCurrentLookupKey() throws Exception {
        Method method = DynamicRoutingDataSource.class.getDeclaredMethod("determineCurrentLookupKey");
        method.setAccessible(true);
        return method.invoke(routingDataSource);
    }
}
