package com.saasbasics.platform.outbox;

import java.time.LocalDateTime;

public record OutboxEventRecordView(
        Long id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        Long tenantId,
        String payloadJson,
        String status,
        int retryCount,
        int maxRetries,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
}
