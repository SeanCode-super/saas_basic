package com.saasbasics.platform.common.api;

import java.net.URI;
import java.time.Instant;

/**
 * RFC 9457 Problem Details for HTTP APIs (application/problem+json).
 */
public record ProblemDetails(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String code,
        String traceId,
        Instant timestamp
) {
    public static ProblemDetails of(int status, String title, String detail, String code, String instancePath, String traceId) {
        String safeCode = (code != null && !code.isBlank()) ? code : "ERROR";
        URI typeUri = URI.create("urn:tianshu:problem:" + safeCode.toLowerCase().replace('_', '-'));
        URI instanceUri = instancePath != null && !instancePath.isBlank() ? URI.create(instancePath) : URI.create("/");
        return new ProblemDetails(
                typeUri,
                title,
                status,
                detail,
                instanceUri,
                safeCode,
                traceId,
                Instant.now()
        );
    }
}
