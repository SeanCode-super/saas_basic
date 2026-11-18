package com.saasbasics.platform.modules.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookDeliveryResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionResponse;
import com.saasbasics.platform.modules.integration.dto.WebhookDtos.WebhookSubscriptionSaveRequest;
import com.saasbasics.platform.modules.integration.entity.WebhookDeliveryLogEntity;
import com.saasbasics.platform.modules.integration.entity.WebhookSubscriptionEntity;
import com.saasbasics.platform.modules.integration.mapper.WebhookDeliveryLogMapper;
import com.saasbasics.platform.modules.integration.mapper.WebhookSubscriptionMapper;
import com.saasbasics.platform.outbox.CloudEventEnvelope;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 出站 CloudEvents 1.0 Webhook 订阅、HMAC-SHA256 签名投递与死信重放服务。
 */
@Service
public class WebhookSubscriptionService {

    private final ObjectProvider<WebhookSubscriptionMapper> subscriptionMapperProvider;
    private final ObjectProvider<WebhookDeliveryLogMapper> deliveryLogMapperProvider;
    private final ObjectProvider<CredentialVaultService> credentialVaultProvider;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public WebhookSubscriptionService(
            ObjectProvider<WebhookSubscriptionMapper> subscriptionMapperProvider,
            ObjectProvider<WebhookDeliveryLogMapper> deliveryLogMapperProvider,
            ObjectProvider<CredentialVaultService> credentialVaultProvider,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.subscriptionMapperProvider = subscriptionMapperProvider;
        this.deliveryLogMapperProvider = deliveryLogMapperProvider;
        this.credentialVaultProvider = credentialVaultProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.environment = environment;
    }

    public List<WebhookSubscriptionResponse> listSubscriptions() {
        WebhookSubscriptionMapper mapper = requiredSubscriptionMapper();
        Long tenantId = currentTenantId();
        return mapper.selectList(new LambdaQueryWrapper<WebhookSubscriptionEntity>()
                        .eq(WebhookSubscriptionEntity::getTenantId, tenantId)
                        .eq(WebhookSubscriptionEntity::getDeleted, 0)
                        .orderByDesc(WebhookSubscriptionEntity::getId))
                .stream()
                .map(entity -> toSubscriptionResponse(entity, null))
                .toList();
    }

    @Transactional
    public WebhookSubscriptionResponse createSubscription(WebhookSubscriptionSaveRequest request) {
        if (request == null || request.subscriptionName() == null || request.subscriptionName().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "订阅名称不能为空");
        }
        if (request.targetUrl() == null || request.targetUrl().isBlank()) {
            throw new BizException("VALIDATION_ERROR", "目标接收 URL 不能为空");
        }
        URI validatedUri = SsrfProtectionValidator.validateOutboundUri(request.targetUrl().trim(), isPrivateAllowed());
        String rawSecret = (request.secret() != null && !request.secret().isBlank())
                ? request.secret().trim()
                : generateWebhookSecret();

        CredentialVaultService vault = credentialVaultProvider != null ? credentialVaultProvider.getIfAvailable() : null;
        String encryptedSecret = vault != null ? vault.encrypt(rawSecret) : rawSecret;

        LocalDateTime now = LocalDateTime.now();
        WebhookSubscriptionEntity entity = new WebhookSubscriptionEntity();
        entity.setPublicId(uuidGenerator.generate().toString());
        entity.setTenantId(currentTenantId());
        entity.setSubscriptionName(request.subscriptionName().trim());
        entity.setTargetUrl(validatedUri.toString());
        entity.setEventTypes((request.eventTypes() != null && !request.eventTypes().isBlank())
                ? request.eventTypes().trim()
                : "*");
        entity.setSecretCiphertext(encryptedSecret);
        entity.setStatus("ENABLED");
        entity.setTimeoutMs(request.timeoutMs() != null ? Math.max(500, Math.min(30_000, request.timeoutMs())) : 5000);
        entity.setMaxRetries(request.maxRetries() != null ? Math.max(0, Math.min(10, request.maxRetries())) : 3);
        entity.setCreatedBy(currentUserId());
        entity.setUpdatedBy(currentUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);

        requiredSubscriptionMapper().insert(entity);
        return toSubscriptionResponse(entity, rawSecret);
    }

    @Transactional
    public void deleteSubscription(Long id) {
        WebhookSubscriptionMapper mapper = requiredSubscriptionMapper();
        WebhookSubscriptionEntity entity = requireOwnedSubscription(mapper, id);
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.deleteById(entity.getId());
    }

    public List<WebhookDeliveryResponse> listDeliveries() {
        WebhookDeliveryLogMapper mapper = requiredDeliveryLogMapper();
        Long tenantId = currentTenantId();
        return mapper.selectList(new LambdaQueryWrapper<WebhookDeliveryLogEntity>()
                        .eq(WebhookDeliveryLogEntity::getTenantId, tenantId)
                        .orderByDesc(WebhookDeliveryLogEntity::getId)
                        .last("LIMIT 50"))
                .stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    @Transactional
    public WebhookDeliveryResponse sendTestEvent(Long subscriptionId, String eventTypeOverride) {
        WebhookSubscriptionEntity sub = requireOwnedSubscription(requiredSubscriptionMapper(), subscriptionId);
        String eventType = resolveTestEventType(sub.getEventTypes(), eventTypeOverride);
        String eventId = UUID.randomUUID().toString();
        CloudEventEnvelope envelope = CloudEventEnvelope.create(
                eventId,
                eventType,
                "subscription/" + sub.getPublicId(),
                sub.getTenantId(),
                Map.of(
                        "ping", true,
                        "subscriptionId", sub.getId(),
                        "subscriptionName", sub.getSubscriptionName()
                )
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new BizException("WEBHOOK_SERIALIZE_ERROR", "CloudEvents 报文序列化失败: " + ex.getMessage());
        }

        String secret = decryptSecret(sub.getSecretCiphertext());
        DispatchResult result = dispatchHttp(sub.getTargetUrl(), sub.getTimeoutMs(), envelope, payloadJson, secret);

        WebhookDeliveryLogEntity logEntity = new WebhookDeliveryLogEntity();
        logEntity.setEventId(eventId);
        logEntity.setTenantId(sub.getTenantId());
        logEntity.setSubscriptionId(sub.getId());
        logEntity.setEventType(eventType);
        logEntity.setTargetUrl(sub.getTargetUrl());
        logEntity.setCloudEventJson(payloadJson);
        logEntity.setHttpStatus(result.httpStatus());
        logEntity.setResponseExcerpt(result.responseExcerpt());
        logEntity.setRetryCount(0);
        logEntity.setDurationMs(result.durationMs());
        logEntity.setDeliveredAt(LocalDateTime.now());
        logEntity.setCreatedAt(LocalDateTime.now());
        logEntity.setStatus(result.success() ? "SUCCESS" : "FAILED");

        requiredDeliveryLogMapper().insert(logEntity);
        return toDeliveryResponse(logEntity);
    }

    @Transactional
    public WebhookDeliveryResponse replayDelivery(Long logId) {
        WebhookDeliveryLogMapper logMapper = requiredDeliveryLogMapper();
        WebhookDeliveryLogEntity logEntity = logMapper.selectOne(new LambdaQueryWrapper<WebhookDeliveryLogEntity>()
                .eq(WebhookDeliveryLogEntity::getId, logId)
                .eq(WebhookDeliveryLogEntity::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        if (logEntity == null) {
            throw new BizException("WEBHOOK_DELIVERY_NOT_FOUND", "Webhook 投递记录不存在: " + logId);
        }

        WebhookSubscriptionEntity sub = requiredSubscriptionMapper().selectOne(new LambdaQueryWrapper<WebhookSubscriptionEntity>()
                .eq(WebhookSubscriptionEntity::getId, logEntity.getSubscriptionId())
                .eq(WebhookSubscriptionEntity::getTenantId, currentTenantId())
                .eq(WebhookSubscriptionEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (sub == null) {
            throw new BizException("WEBHOOK_NOT_FOUND", "关联 Webhook 订阅已删除或不可用");
        }

        String secret = decryptSecret(sub.getSecretCiphertext());
        CloudEventEnvelope envelope = CloudEventEnvelope.create(
                logEntity.getEventId(),
                logEntity.getEventType(),
                "subscription/" + sub.getPublicId(),
                logEntity.getTenantId(),
                Map.of()
        );
        DispatchResult result = dispatchHttp(
                logEntity.getTargetUrl(),
                sub.getTimeoutMs(),
                envelope,
                logEntity.getCloudEventJson(),
                secret
        );

        int nextRetry = (logEntity.getRetryCount() == null ? 0 : logEntity.getRetryCount()) + 1;
        int maxRetries = sub.getMaxRetries() == null ? 3 : sub.getMaxRetries();
        logEntity.setHttpStatus(result.httpStatus());
        logEntity.setResponseExcerpt(result.responseExcerpt());
        logEntity.setRetryCount(nextRetry);
        logEntity.setDurationMs(result.durationMs());
        logEntity.setDeliveredAt(LocalDateTime.now());
        if (result.success()) {
            logEntity.setStatus("SUCCESS");
        } else {
            logEntity.setStatus(nextRetry >= maxRetries ? "DEAD_LETTER" : "FAILED");
        }
        logMapper.updateById(logEntity);
        return toDeliveryResponse(logEntity);
    }

    static String computeHmacSha256Signature(String secret, String payloadJson) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] keyBytes = (secret != null ? secret : "").getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal((payloadJson != null ? payloadJson : "").getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private DispatchResult dispatchHttp(
            String targetUrl,
            Integer timeoutMs,
            CloudEventEnvelope envelope,
            String payloadJson,
            String secret
    ) {
        long startNs = System.nanoTime();
        int timeout = timeoutMs != null ? Math.max(500, Math.min(30_000, timeoutMs)) : 5000;
        try {
            URI uri = SsrfProtectionValidator.validateOutboundUri(targetUrl, isPrivateAllowed());
            String signatureHeader = computeHmacSha256Signature(secret, payloadJson);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeout))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/cloudevents+json; charset=UTF-8")
                    .header("ce-specversion", CloudEventEnvelope.SPEC_VERSION)
                    .header("ce-id", envelope.id())
                    .header("ce-type", envelope.type())
                    .header("ce-source", envelope.source())
                    .header("X-Webhook-Signature-256", signatureHeader)
                    .header("X-SaaS-Signature", signatureHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            int status = resp.statusCode();
            boolean ok = status >= 200 && status < 300;
            return new DispatchResult(ok, status, truncate(resp.body(), 480), durationMs);
        } catch (Exception ex) {
            long durationMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new DispatchResult(false, 0, truncate(ex.getMessage(), 480), durationMs);
        }
    }

    private WebhookSubscriptionEntity requireOwnedSubscription(WebhookSubscriptionMapper mapper, Long id) {
        WebhookSubscriptionEntity entity = mapper.selectOne(new LambdaQueryWrapper<WebhookSubscriptionEntity>()
                .eq(WebhookSubscriptionEntity::getId, id)
                .eq(WebhookSubscriptionEntity::getTenantId, currentTenantId())
                .eq(WebhookSubscriptionEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException("WEBHOOK_NOT_FOUND", "Webhook 订阅配置不存在或无权访问: " + id);
        }
        return entity;
    }

    private WebhookSubscriptionResponse toSubscriptionResponse(WebhookSubscriptionEntity e, String rawSecretOnce) {
        return new WebhookSubscriptionResponse(
                e.getId(),
                e.getPublicId(),
                e.getSubscriptionName(),
                e.getTargetUrl(),
                e.getEventTypes(),
                maskSecret(decryptSecret(e.getSecretCiphertext())),
                rawSecretOnce,
                e.getStatus(),
                e.getTimeoutMs(),
                e.getMaxRetries(),
                e.getCreatedAt()
        );
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDeliveryLogEntity e) {
        return new WebhookDeliveryResponse(
                e.getId(),
                e.getEventId(),
                e.getSubscriptionId(),
                e.getEventType(),
                e.getTargetUrl(),
                e.getHttpStatus(),
                e.getResponseExcerpt(),
                e.getStatus(),
                e.getRetryCount(),
                e.getDurationMs(),
                e.getDeliveredAt(),
                e.getCreatedAt()
        );
    }

    private String decryptSecret(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        CredentialVaultService vault = credentialVaultProvider != null ? credentialVaultProvider.getIfAvailable() : null;
        return vault != null ? vault.decrypt(stored) : stored;
    }

    private String maskSecret(String raw) {
        if (raw == null || raw.length() <= 8) {
            return "whsec_****";
        }
        return raw.substring(0, 6) + "****" + raw.substring(raw.length() - 4);
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private String resolveTestEventType(String configuredTypes, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (configuredTypes != null && !configuredTypes.isBlank() && !"*".equals(configuredTypes.trim())) {
            String first = configuredTypes.split(",")[0].trim();
            if (!first.isEmpty() && !"*".equals(first)) {
                return first;
            }
        }
        return "platform.webhook.ping";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[\\r\\n]+", " ").trim();
        return clean.length() <= maxLen ? clean : clean.substring(0, maxLen);
    }

    private boolean isPrivateAllowed() {
        return environment == null || !environment.acceptsProfiles(Profiles.of("prod"));
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }

    private Long currentUserId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.userId() != null ? principal.userId() : 0L;
    }

    private WebhookSubscriptionMapper requiredSubscriptionMapper() {
        WebhookSubscriptionMapper mapper = subscriptionMapperProvider != null
                ? subscriptionMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            throw new BizException("MAPPER_UNAVAILABLE", "WebhookSubscriptionMapper 不可用");
        }
        return mapper;
    }

    private WebhookDeliveryLogMapper requiredDeliveryLogMapper() {
        WebhookDeliveryLogMapper mapper = deliveryLogMapperProvider != null
                ? deliveryLogMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            throw new BizException("MAPPER_UNAVAILABLE", "WebhookDeliveryLogMapper 不可用");
        }
        return mapper;
    }

    private record DispatchResult(boolean success, int httpStatus, String responseExcerpt, long durationMs) {
    }
}
