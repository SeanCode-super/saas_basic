package com.saasbasics.platform.modules.integration.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public final class WebhookDtos {

    private WebhookDtos() {
    }

    public record WebhookSubscriptionSaveRequest(
            @NotBlank(message = "订阅名称不能为空")
            String subscriptionName,
            @NotBlank(message = "目标接收 URL 不能为空")
            String targetUrl,
            String eventTypes,
            String secret,
            Integer timeoutMs,
            Integer maxRetries
    ) {
    }

    public record WebhookSubscriptionResponse(
            Long id,
            String publicId,
            String subscriptionName,
            String targetUrl,
            String eventTypes,
            String maskedSecret,
            String rawSecretOnce,
            String status,
            Integer timeoutMs,
            Integer maxRetries,
            LocalDateTime createdAt
    ) {
    }

    public record WebhookDeliveryResponse(
            Long id,
            String eventId,
            Long subscriptionId,
            String eventType,
            String targetUrl,
            Integer httpStatus,
            String responseExcerpt,
            String status,
            Integer retryCount,
            Long durationMs,
            LocalDateTime deliveredAt,
            LocalDateTime createdAt
    ) {
    }
}
