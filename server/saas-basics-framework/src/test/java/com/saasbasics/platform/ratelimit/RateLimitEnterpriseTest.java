package com.saasbasics.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitEnterpriseTest {

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    void enforcesSlidingWindowAndTracksPrometheusMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("meterRegistry", meterRegistry);

        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                bf.getBeanProvider(StringRedisTemplate.class),
                bf.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class)
        );

        RateLimitDecision d1 = limiter.tryAcquire("test:bucket", 2, 60, "TENANT");
        RateLimitDecision d2 = limiter.tryAcquire("test:bucket", 2, 60, "TENANT");
        RateLimitDecision d3 = limiter.tryAcquire("test:bucket", 2, 60, "TENANT");

        assertThat(d1.allowed()).isTrue();
        assertThat(d1.remaining()).isEqualTo(1);
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.remaining()).isZero();
        assertThat(d3.allowed()).isFalse();
        assertThat(d3.remaining()).isZero();

        assertThat(meterRegistry.get("saas_ratelimit_requests_total")
                .tag("dimension", "TENANT").tag("outcome", "allowed").counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.get("saas_ratelimit_requests_total")
                .tag("dimension", "TENANT").tag("outcome", "rejected").counter().count()).isEqualTo(1.0);
    }

    @Test
    void globalApiInterceptorIsolatesTenantsAndSetsRateLimitHeaders() {
        RateLimitProperties props = new RateLimitProperties();
        props.setAiTenantRpm(2);
        props.setTenantRpm(100);
        props.setWindowSeconds(60);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                bf.getBeanProvider(StringRedisTemplate.class),
                bf.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class)
        );
        GlobalApiRateLimitInterceptor interceptor = new GlobalApiRateLimitInterceptor(props, limiter);

        AuthContext.set(principalForTenant(101L));
        MockHttpServletRequest aiReq = new MockHttpServletRequest("POST", "/api/ai/chat");
        MockHttpServletResponse aiResp = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(aiReq, aiResp, new Object())).isTrue();
        assertThat(aiResp.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(aiResp.getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        assertThat(interceptor.preHandle(aiReq, aiResp, new Object())).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(aiReq, aiResp, new Object()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(aiResp.getHeader("Retry-After")).isNotBlank();

        // Tenant 202 remains completely unaffected
        AuthContext.set(principalForTenant(202L));
        MockHttpServletResponse tenant202Resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(aiReq, tenant202Resp, new Object())).isTrue();
        assertThat(tenant202Resp.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
    }

    private AuthPrincipal principalForTenant(Long tenantId) {
        return new AuthPrincipal(
                1L, tenantId, "t" + tenantId, 10L, "tester", "Tester",
                "STAFF", "ONLINE", LocalDateTime.now().plusHours(1),
                List.of("*"), List.of(), List.of(), List.of(),
                UUID.randomUUID(), UUID.randomUUID(), null, null, null
        );
    }
}
