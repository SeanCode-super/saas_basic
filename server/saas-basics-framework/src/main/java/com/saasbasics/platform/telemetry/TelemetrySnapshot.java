package com.saasbasics.platform.telemetry;

import java.util.List;

public record TelemetrySnapshot(
        String serviceName,
        boolean otelEnabled,
        boolean otlpEnabled,
        String otlpEndpoint,
        String prometheusEndpoint,
        long httpTotalRequests,
        long httpErrorRequests,
        double avgLatencyMs,
        long rateLimitAllowed,
        long rateLimitRejected,
        long aiRequestsTotal,
        long aiPromptTokensTotal,
        long aiCompletionTokensTotal,
        boolean vaultUsingDefaultKey,
        String vaultActiveKeyVersion,
        int tenantRpmLimit,
        int aiTenantRpmLimit,
        List<TraceRecord> recentTraces
) {
}
