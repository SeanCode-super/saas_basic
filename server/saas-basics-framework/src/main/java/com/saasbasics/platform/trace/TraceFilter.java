package com.saasbasics.platform.trace;

import com.saasbasics.platform.common.trace.TraceContext;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * OpenTelemetry 全链路追踪与日志染色过滤器（兼容 W3C traceparent 与 X-Trace-Id）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String ATTR_TENANT_ID = "saas.telemetry.tenantId";
    public static final String ATTR_USER_ID = "saas.telemetry.userId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_USER_ID = "userId";

    private final ObjectProvider<PlatformTelemetryService> telemetryServiceProvider;

    public TraceFilter(ObjectProvider<PlatformTelemetryService> telemetryServiceProvider) {
        this.telemetryServiceProvider = telemetryServiceProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        PlatformTelemetryService telemetryService = telemetryServiceProvider.getIfAvailable();
        long startMillis = System.currentTimeMillis();
        Span span = null;
        Scope otelScope = null;
        String traceId;
        String spanId = "";

        if (telemetryService != null) {
            Context parentContext = telemetryService.extractParentContext(request);
            span = telemetryService.startServerSpan(request, parentContext);
            otelScope = span.makeCurrent();
            traceId = span.getSpanContext().getTraceId();
            spanId = span.getSpanContext().getSpanId();
            // Honor explicit incoming X-Trace-Id if caller passed a non-W3C correlation token without traceparent
            String headerTraceId = request.getHeader(TRACE_HEADER);
            if (request.getHeader("traceparent") == null && headerTraceId != null && !headerTraceId.isBlank()) {
                traceId = headerTraceId.trim();
            }
            telemetryService.injectTraceHeaders(Context.current(), response);
        } else {
            String headerTraceId = request.getHeader(TRACE_HEADER);
            traceId = (headerTraceId == null || headerTraceId.isBlank())
                    ? UUID.randomUUID().toString().replace("-", "")
                    : headerTraceId.trim();
        }

        TraceContext.setTraceId(traceId);
        TraceContext.setSpanId(spanId);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        response.setHeader(TRACE_HEADER, traceId);

        Throwable caughtError = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            caughtError = ex;
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - startMillis;
            Long tenantId = toLong(request.getAttribute(ATTR_TENANT_ID));
            Long userId = toLong(request.getAttribute(ATTR_USER_ID));
            if (telemetryService != null) {
                telemetryService.finishServerSpan(
                        span,
                        request.getMethod(),
                        request.getRequestURI(),
                        tenantId,
                        userId,
                        response.getStatus(),
                        durationMs,
                        caughtError
                );
            }
            if (otelScope != null) {
                otelScope.close();
            }
            MDC.clear();
            TraceContext.clear();
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
