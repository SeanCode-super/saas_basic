package com.saasbasics.platform.outbox;

public record OutboxEventPayload(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        String payloadJson
) {
}
