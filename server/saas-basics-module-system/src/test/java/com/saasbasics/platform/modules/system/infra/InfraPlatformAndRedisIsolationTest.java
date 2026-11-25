package com.saasbasics.platform.modules.system.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.cache.SystemCacheService;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.system.controller.InfraMonitorController;
import com.saasbasics.platform.modules.system.controller.ServerMonitorController;
import com.saasbasics.platform.modules.system.infra.InfraMonitorService.RedisKeyItem;
import com.saasbasics.platform.modules.system.service.ServerMonitorService;
import com.saasbasics.platform.security.PermissionGuardInterceptor;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class InfraPlatformAndRedisIsolationTest {

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("普通租户管理员（即使名为 admin 且持通配权限）调用 @RequirePlatformTenant 宿主机接口应被拦截")
    void blocksNonPlatformTenantFromHostOpsEndpointsEvenIfSuperAdminNamedAdmin() throws Exception {
        PermissionGuardInterceptor interceptor = new PermissionGuardInterceptor();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ServerMonitorController serverController = new ServerMonitorController(
                new ServerMonitorService(
                        beanFactory.getBeanProvider(DataSource.class),
                        beanFactory.getBeanProvider(InfraMonitorService.class),
                        org.mockito.Mockito.mock(com.saasbasics.platform.modules.system.mapper.PlatformWorkflowMetricsMapper.class)
                ),
                beanFactory.getBeanProvider(PlatformTelemetryService.class)
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<RedisTemplate<String, Object>> redisProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        InfraMonitorController infraController = new InfraMonitorController(
                new InfraMonitorService(),
                new SystemCacheService(redisProvider)
        );

        HandlerMethod serverMethod = new HandlerMethod(serverController, ServerMonitorController.class.getMethod("getServerMonitor"));
        Method slowSqlMethod = InfraMonitorController.class.getMethod("getSlowSqlReport");
        HandlerMethod slowSqlHandler = new HandlerMethod(infraController, slowSqlMethod);

        AuthPrincipal tenantTwoAdmin = new AuthPrincipal(
                99L, 2L, "acme", 100L, "admin", "Acme Admin", "TENANT_ADMIN", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of("*"), List.of("*"), List.of(1L), List.of("*")
        );
        AuthContext.set(tenantTwoAdmin);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/infra/server-monitor");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(req, resp, serverMethod))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PLATFORM_TENANT_REQUIRED"));

        assertThatThrownBy(() -> interceptor.preHandle(req, resp, slowSqlHandler))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PLATFORM_TENANT_REQUIRED"));

        AuthPrincipal platformAdmin = new AuthPrincipal(
                1L, 1L, "platform", 1L, "admin", "Platform Admin", "SUPER_ADMIN", "ACTIVE",
                LocalDateTime.now().plusHours(1), List.of("*"), List.of("*"), List.of(1L), List.of("*")
        );
        AuthContext.set(platformAdmin);
        assertThat(interceptor.preHandle(req, resp, serverMethod)).isTrue();
        assertThat(interceptor.preHandle(req, resp, slowSqlHandler)).isTrue();
    }

    @Test
    @DisplayName("非平台租户的 Redis 键列举与删除严格限制在 saas:t:{tenantId}: 命名空间内")
    void enforcesTenantRedisNamespaceIsolation() {
        InfraMonitorService service = new InfraMonitorService();
        List<RedisKeyItem> tenantKeys = service.listRedisKeys(2L, false, "*");
        assertThat(tenantKeys).isNotEmpty();
        assertThat(tenantKeys).allSatisfy(item -> assertThat(item.key()).startsWith("saas:t:2:"));

        assertThatThrownBy(() -> service.deleteRedisKey(2L, false, "saas:t:1:iam:menus"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("REDIS_NAMESPACE_FORBIDDEN"));

        assertThat(service.deleteRedisKey(2L, false, "saas:t:2:iam:menus")).isTrue();
    }
}
