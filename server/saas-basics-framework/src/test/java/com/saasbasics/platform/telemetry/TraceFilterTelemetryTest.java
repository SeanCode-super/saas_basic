package com.saasbasics.platform.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasbasics.platform.common.trace.TraceContext;
import com.saasbasics.platform.ratelimit.RateLimitProperties;
import com.saasbasics.platform.ratelimit.SlidingWindowRateLimiter;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.trace.TraceFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceFilterTelemetryTest {

    @Test
    void createsW3cOpenTelemetrySpanRecordsMetricsAndCleansMdc() throws Exception {
        TelemetryProperties props = new TelemetryProperties();
        props.setServiceName("saas-basics-test");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("meterRegistry", meterRegistry);
        bf.addBean("rateLimitProperties", new RateLimitProperties());

        PlatformTelemetryService telemetry = new PlatformTelemetryService(
                props,
                bf.getBeanProvider(MeterRegistry.class),
                bf.getBeanProvider(SlidingWindowRateLimiter.class),
                bf.getBeanProvider(RateLimitProperties.class),
                bf.getBeanProvider(CredentialVaultService.class)
        );
        bf.addBean("platformTelemetryService", telemetry);

        TraceFilter filter = new TraceFilter(bf.getBeanProvider(PlatformTelemetryService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/models");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            req.setAttribute(TraceFilter.ATTR_TENANT_ID, 77L);
            assertThat(TraceContext.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            ((MockHttpServletResponse) res).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getHeader("traceparent")).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(TraceContext.getTraceId()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        TelemetrySnapshot snapshot = telemetry.getSnapshot();
        assertThat(snapshot.httpTotalRequests()).isEqualTo(1);
        assertThat(snapshot.recentTraces()).hasSize(1);
        assertThat(snapshot.recentTraces().get(0).tenantId()).isEqualTo(77L);
        assertThat(meterRegistry.get("saas_http_server_requests_seconds").timer().count()).isEqualTo(1);

        telemetry.shutdown();
    }
}
