package com.saasbasics.platform.telemetry;

public record TraceRecord(
        String traceId,
        String spanId,
        String parentSpanId,
        String method,
        String uri,
        Long tenantId,
        Long userId,
        int statusCode,
        long durationMs,
        String outcome,
        String timestamp
) {
}
