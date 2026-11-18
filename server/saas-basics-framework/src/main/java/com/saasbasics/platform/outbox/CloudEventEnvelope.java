package com.saasbasics.platform.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * CloudEvents 1.0 Specification Envelope (RFC 3339 UTC timestamp + idempotent source/id).
 */
public record CloudEventEnvelope(
        String specversion,
        String id,
        String source,
        String type,
        String subject,
        String time,
        String datacontenttype,
        Long tenantid,
        Object data
) {
    public static final String SPEC_VERSION = "1.0";
    public static final String DEFAULT_SOURCE = "urn:tianshu:saas-basics";

    public static CloudEventEnvelope create(String eventId, String type, String subject, Long tenantId, Object data) {
        return new CloudEventEnvelope(
                SPEC_VERSION,
                (eventId != null && !eventId.isBlank()) ? eventId : UUID.randomUUID().toString(),
                DEFAULT_SOURCE,
                type,
                subject,
                Instant.now().toString(),
                "application/json",
                tenantId,
                data
        );
    }
}
