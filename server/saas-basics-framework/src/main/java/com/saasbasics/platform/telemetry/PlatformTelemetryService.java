package com.saasbasics.platform.telemetry;

import com.saasbasics.platform.ratelimit.RateLimitProperties;
import com.saasbasics.platform.ratelimit.SlidingWindowRateLimiter;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 平台全链路 OpenTelemetry 追踪与 Micrometer Prometheus 指标聚合服务。
 */
@Service
@EnableConfigurationProperties(TelemetryProperties.class)
public class PlatformTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(PlatformTelemetryService.class);

    private static final TextMapGetter<HttpServletRequest> REQUEST_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return carrier != null ? Collections.list(carrier.getHeaderNames()) : List.of();
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier != null ? carrier.getHeader(key) : null;
        }
    };

    private static final TextMapSetter<HttpServletResponse> RESPONSE_SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.setHeader(key, value);
        }
    };

    private final TelemetryProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<SlidingWindowRateLimiter> rateLimiterProvider;
    private final ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider;
    private final ObjectProvider<CredentialVaultService> credentialVaultProvider;

    private final SdkTracerProvider sdkTracerProvider;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    private final Deque<TraceRecord> recentTraces = new ConcurrentLinkedDeque<>();
    private final AtomicLong httpTotalRequests = new AtomicLong(0);
    private final AtomicLong httpErrorRequests = new AtomicLong(0);
    private final AtomicLong httpTotalDurationMs = new AtomicLong(0);
    private final AtomicLong aiRequestsTotal = new AtomicLong(0);
    private final AtomicLong aiPromptTokensTotal = new AtomicLong(0);
    private final AtomicLong aiCompletionTokensTotal = new AtomicLong(0);

    public PlatformTelemetryService(
            TelemetryProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<SlidingWindowRateLimiter> rateLimiterProvider,
            ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
            ObjectProvider<CredentialVaultService> credentialVaultProvider
    ) {
        this.properties = properties;
        this.meterRegistryProvider = meterRegistryProvider;
        this.rateLimiterProvider = rateLimiterProvider;
        this.rateLimitPropertiesProvider = rateLimitPropertiesProvider;
        this.credentialVaultProvider = credentialVaultProvider;

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), properties.getServiceName()
                ))
        );
        SdkTracerProviderBuilder providerBuilder = SdkTracerProvider.builder().setResource(resource);
        if (properties.isOtlpEnabled() && properties.getOtlpEndpoint() != null && !properties.getOtlpEndpoint().isBlank()) {
            providerBuilder.addSpanProcessor(
                    BatchSpanProcessor.builder(new OtlpHttpJsonSpanExporter(properties.getOtlpEndpoint())).build()
            );
            log.info("[OpenTelemetry] OTLP HTTP exporter enabled -> {}", properties.getOtlpEndpoint());
        }
        this.sdkTracerProvider = providerBuilder.build();
        this.openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        this.tracer = openTelemetry.getTracer("com.saasbasics.platform", "1.0.0");
    }

    public Context extractParentContext(HttpServletRequest request) {
        return openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), request, REQUEST_GETTER);
    }

    public void injectTraceHeaders(Context context, HttpServletResponse response) {
        openTelemetry.getPropagators().getTextMapPropagator().inject(context, response, RESPONSE_SETTER);
    }

    public Span startServerSpan(HttpServletRequest request, Context parentContext) {
        String method = request.getMethod() != null ? request.getMethod() : "UNKNOWN";
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "/";
        return tracer.spanBuilder("HTTP " + method + " " + uri)
                .setParent(parentContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", method)
                .setAttribute("http.route", uri)
                .startSpan();
    }

    public void finishServerSpan(
            Span span,
            String method,
            String uri,
            Long tenantId,
            Long userId,
            int statusCode,
            long durationMs,
            Throwable error
    ) {
        if (span != null) {
            span.setAttribute("http.status_code", statusCode);
            if (tenantId != null) {
                span.setAttribute("tenant.id", tenantId);
            }
            if (userId != null) {
                span.setAttribute("user.id", userId);
            }
            if (error != null || statusCode >= 500) {
                span.setStatus(StatusCode.ERROR, error != null ? error.getMessage() : ("HTTP " + statusCode));
                if (error != null) {
                    span.recordException(error);
                }
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
        }

        // Skip actuator polling noise from inflating recentTraces list if desired, but record metrics
        httpTotalRequests.incrementAndGet();
        httpTotalDurationMs.addAndGet(Math.max(0L, durationMs));
        if (statusCode >= 500 || error != null) {
            httpErrorRequests.incrementAndGet();
        }

        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            String outcome = statusCode >= 500 ? "SERVER_ERROR" : (statusCode >= 400 ? "CLIENT_ERROR" : "SUCCESS");
            Timer.builder("saas_http_server_requests_seconds")
                    .description("Platform HTTP request latency tracked with tenant and status tags")
                    .tag("method", method != null ? method : "UNKNOWN")
                    .tag("status", String.valueOf(statusCode))
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
        }

        if (uri != null && !uri.startsWith("/actuator") && properties.isEnabled()) {
            String traceId = span != null ? span.getSpanContext().getTraceId() : "";
            String spanId = span != null ? span.getSpanContext().getSpanId() : "";
            String outcome = statusCode >= 500 ? "ERROR" : (statusCode == 429 ? "RATE_LIMITED" : (statusCode >= 400 ? "WARN" : "OK"));
            TraceRecord record = new TraceRecord(
                    traceId,
                    spanId,
                    "",
                    method != null ? method : "GET",
                    uri,
                    tenantId,
                    userId,
                    statusCode,
                    durationMs,
                    outcome,
                    Instant.now().toString()
            );
            recentTraces.addFirst(record);
            int max = Math.max(10, properties.getMaxRecentTraces());
            while (recentTraces.size() > max) {
                recentTraces.pollLast();
            }
        }
    }

    public void recordAiUsage(String providerCode, String modelId, String status, int promptTokens, int completionTokens, long latencyMs) {
        aiRequestsTotal.incrementAndGet();
        aiPromptTokensTotal.addAndGet(Math.max(0, promptTokens));
        aiCompletionTokensTotal.addAndGet(Math.max(0, completionTokens));

        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            String prov = providerCode != null ? providerCode : "unknown";
            String mod = modelId != null ? modelId : "unknown";
            String st = status != null ? status : "SUCCEEDED";
            Counter.builder("saas_ai_requests_total")
                    .description("Total AI model invocations")
                    .tag("provider", prov)
                    .tag("model", mod)
                    .tag("status", st)
                    .register(registry)
                    .increment();

            if (promptTokens > 0) {
                Counter.builder("saas_ai_tokens_total")
                        .description("Total AI tokens consumed")
                        .tag("provider", prov)
                        .tag("model", mod)
                        .tag("type", "prompt")
                        .register(registry)
                        .increment(promptTokens);
            }
            if (completionTokens > 0) {
                Counter.builder("saas_ai_tokens_total")
                        .description("Total AI tokens consumed")
                        .tag("provider", prov)
                        .tag("model", mod)
                        .tag("type", "completion")
                        .register(registry)
                        .increment(completionTokens);
            }
            Timer.builder("saas_ai_request_duration_seconds")
                    .description("AI request processing duration")
                    .tag("provider", prov)
                    .tag("model", mod)
                    .register(registry)
                    .record(Math.max(0L, latencyMs), TimeUnit.MILLISECONDS);
        }
    }

    public TelemetrySnapshot getSnapshot() {
        long total = httpTotalRequests.get();
        double avgLatency = total > 0 ? Math.round((httpTotalDurationMs.get() * 100.0) / total) / 100.0 : 0.0;
        SlidingWindowRateLimiter limiter = rateLimiterProvider.getIfAvailable();
        RateLimitProperties rlProps = rateLimitPropertiesProvider.getIfAvailable();
        CredentialVaultService vault = credentialVaultProvider.getIfAvailable();

        return new TelemetrySnapshot(
                properties.getServiceName(),
                properties.isEnabled(),
                properties.isOtlpEnabled(),
                properties.getOtlpEndpoint(),
                "/actuator/prometheus",
                total,
                httpErrorRequests.get(),
                avgLatency,
                limiter != null ? limiter.getTotalAllowedCount() : 0L,
                limiter != null ? limiter.getTotalRejectedCount() : 0L,
                aiRequestsTotal.get(),
                aiPromptTokensTotal.get(),
                aiCompletionTokensTotal.get(),
                vault == null || vault.isUsingDefaultMasterKey(),
                vault != null ? vault.activeKeyVersion() : "v1",
                rlProps != null ? rlProps.getTenantRpm() : 1200,
                rlProps != null ? rlProps.getAiTenantRpm() : 60,
                new ArrayList<>(recentTraces)
        );
    }

    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }

    public Tracer getTracer() {
        return tracer;
    }

    @PreDestroy
    public void shutdown() {
        sdkTracerProvider.close();
    }

    /**
     * Lightweight OTLP/HTTP JSON span exporter backed by JDK HttpClient (zero OkHttp/Kotlin dependency).
     */
    private static final class OtlpHttpJsonSpanExporter implements SpanExporter {
        private final URI endpoint;
        private final HttpClient client;

        OtlpHttpJsonSpanExporter(String endpointUrl) {
            this.endpoint = URI.create(endpointUrl.trim());
            this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            if (spans == null || spans.isEmpty()) {
                return CompletableResultCode.ofSuccess();
            }
            StringBuilder json = new StringBuilder("{\"resourceSpans\":[{\"scopeSpans\":[{\"spans\":[");
            boolean first = true;
            for (SpanData span : spans) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"traceId\":\"").append(span.getTraceId())
                        .append("\",\"spanId\":\"").append(span.getSpanId())
                        .append("\",\"name\":\"").append(escapeJson(span.getName()))
                        .append("\",\"startTimeUnixNano\":").append(span.getStartEpochNanos())
                        .append(",\"endTimeUnixNano\":").append(span.getEndEpochNanos())
                        .append("}");
            }
            json.append("]}]}]}");

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(endpoint)
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();
                client.sendAsync(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Best-effort telemetry export should never impact request threads
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        private String escapeJson(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
