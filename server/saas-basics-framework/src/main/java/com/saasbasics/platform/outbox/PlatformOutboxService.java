package com.saasbasics.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台通用 Transactional Outbox（本地事务表发件箱 + 指数退避重试 + 死信兜底）：
 * 业务写入与 Outbox 入库共享同一本地 ACID 事务，由后台分布式加锁 Worker 投递并支持死信手工重放。
 */
@Service
public class PlatformOutboxService {

    private static final Logger log = LoggerFactory.getLogger(PlatformOutboxService.class);
    private static final String LOCK_KEY = "saas:outbox:worker:lock";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

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

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final ObjectProvider<List<OutboxEventHandler>> handlersProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    public PlatformOutboxService(
            ObjectProvider<JdbcTemplate> jdbcProvider,
            ObjectProvider<List<OutboxEventHandler>> handlersProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        this.jdbcProvider = jdbcProvider;
        this.handlersProvider = handlersProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String enqueue(String eventType, String aggregateType, String aggregateId, Long tenantId, Object payload) {
        String eventId = "EVT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String json;
        try {
            json = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize outbox payload failed", ex);
        }
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc != null) {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("""
                    INSERT INTO plat_outbox_event (
                        event_id, event_type, aggregate_type, aggregate_id, scope_tenant_id,
                        payload_json, status, retry_count, max_retries, next_retry_at, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 0, 5, ?, ?, ?)
                    """,
                    eventId,
                    eventType,
                    aggregateType,
                    aggregateId,
                    tenantId,
                    json,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now));
        }
        return eventId;
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 12_000L)
    public void scheduledDispatch() {
        String lockToken = UUID.randomUUID().toString();
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        boolean locked = false;
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, lockToken, Duration.ofSeconds(15));
                if (!Boolean.TRUE.equals(ok)) {
                    return;
                }
                locked = true;
            } catch (Exception ignored) {
            }
        }
        try {
            dispatchPendingEvents();
        } catch (Exception ex) {
            log.warn("[Outbox] 定时派发失败: {}", ex.getMessage());
        } finally {
            if (locked && redis != null) {
                try {
                    redis.execute(UNLOCK_SCRIPT, List.of(LOCK_KEY), lockToken);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public int dispatchPendingEvents() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return 0;
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, event_id, event_type, aggregate_type, aggregate_id, scope_tenant_id,
                           payload_json, retry_count, max_retries
                    FROM plat_outbox_event
                    WHERE status IN ('PENDING', 'FAILED')
                      AND next_retry_at <= NOW()
                    ORDER BY id ASC
                    LIMIT 30
                    """);
        } catch (Exception ex) {
            return 0;
        }

        List<OutboxEventHandler> handlers = handlersProvider != null ? handlersProvider.getIfAvailable(List::of) : List.of();
        int dispatched = 0;
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            String eventId = String.valueOf(row.get("event_id"));
            String eventType = String.valueOf(row.get("event_type"));
            String aggregateType = String.valueOf(row.get("aggregate_type"));
            String aggregateId = String.valueOf(row.get("aggregate_id"));
            Number tenantNum = (Number) row.get("scope_tenant_id");
            Long tenantId = tenantNum != null ? tenantNum.longValue() : null;
            String payloadJson = String.valueOf(row.get("payload_json"));
            int retryCount = ((Number) row.get("retry_count")).intValue();
            int maxRetries = ((Number) row.get("max_retries")).intValue();

            OutboxEventPayload evt = new OutboxEventPayload(eventId, eventType, aggregateType, aggregateId, tenantId, payloadJson);
            List<OutboxEventHandler> matched = handlers.stream()
                    .filter(h -> eventType.equalsIgnoreCase(h.supportedEventType()))
                    .toList();
            try {
                for (OutboxEventHandler h : matched) {
                    h.handle(evt);
                }
                jdbc.update("""
                        UPDATE plat_outbox_event
                        SET status = 'SENT', processed_at = NOW(), updated_at = NOW(), last_error = NULL
                        WHERE id = ?
                        """, id);
                dispatched++;
            } catch (Exception ex) {
                int nextRetry = retryCount + 1;
                String nextStatus = nextRetry >= maxRetries ? "DEAD_LETTER" : "FAILED";
                long backoffSec = Math.min(300L, (long) Math.pow(2, nextRetry) * 5L);
                String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                if (err.length() > 480) {
                    err = err.substring(0, 480);
                }
                jdbc.update("""
                        UPDATE plat_outbox_event
                        SET status = ?, retry_count = ?, next_retry_at = ?, last_error = ?, updated_at = NOW()
                        WHERE id = ?
                        """,
                        nextStatus,
                        nextRetry,
                        Timestamp.valueOf(LocalDateTime.now().plusSeconds(backoffSec)),
                        err,
                        id);
                log.warn("[Outbox] 事件 {} 投递失败 (retry={}/{} -> {}): {}", eventId, nextRetry, maxRetries, nextStatus, err);
            }
        }
        return dispatched;
    }

    public List<OutboxEventRecordView> listRecentEvents(int limit) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        try {
            return jdbc.query("""
                    SELECT id, event_id, event_type, aggregate_type, aggregate_id, scope_tenant_id,
                           payload_json, status, retry_count, max_retries, last_error,
                           next_retry_at, processed_at, created_at
                    FROM plat_outbox_event
                    ORDER BY id DESC
                    LIMIT ?
                    """,
                    (rs, rowNum) -> {
                        Timestamp nextTs = rs.getTimestamp("next_retry_at");
                        Timestamp procTs = rs.getTimestamp("processed_at");
                        Timestamp crtTs = rs.getTimestamp("created_at");
                        long tId = rs.getLong("scope_tenant_id");
                        return new OutboxEventRecordView(
                                rs.getLong("id"),
                                rs.getString("event_id"),
                                rs.getString("event_type"),
                                rs.getString("aggregate_type"),
                                rs.getString("aggregate_id"),
                                rs.wasNull() ? null : tId,
                                rs.getString("payload_json"),
                                rs.getString("status"),
                                rs.getInt("retry_count"),
                                rs.getInt("max_retries"),
                                rs.getString("last_error"),
                                nextTs != null ? nextTs.toLocalDateTime() : null,
                                procTs != null ? procTs.toLocalDateTime() : null,
                                crtTs != null ? crtTs.toLocalDateTime() : null
                        );
                    },
                    safeLimit);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public int retryEvent(long id) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return 0;
        }
        jdbc.update("""
                UPDATE plat_outbox_event
                SET status = 'PENDING', next_retry_at = NOW(), updated_at = NOW()
                WHERE id = ?
                """, id);
        return dispatchPendingEvents();
    }
}
