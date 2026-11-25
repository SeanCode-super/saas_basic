package com.saasbasics.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.saasbasics.platform.outbox.mapper.OutboxEventMapper;
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

    private final ObjectProvider<OutboxEventMapper> mapperProvider;
    private final ObjectProvider<List<OutboxEventHandler>> handlersProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    public PlatformOutboxService(
            ObjectProvider<OutboxEventMapper> mapperProvider,
            ObjectProvider<List<OutboxEventHandler>> handlersProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        this.mapperProvider = mapperProvider;
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
        mapperProvider.getObject().enqueue(eventId, eventType, aggregateType, aggregateId, tenantId, json);
        return eventId;
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 12_000L)
    public void scheduledDispatch() {
        String lockToken = UUID.randomUUID().toString();
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        boolean locked = false;
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, lockToken, Duration.ofSeconds(15));
                if (!Boolean.TRUE.equals(ok)) {
                    return;
                }
                locked = true;
            } catch (org.springframework.dao.DataAccessException e) {
                log.warn("Unable to acquire the outbox worker lock", e);
                return;
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
                } catch (org.springframework.dao.DataAccessException e) {
                    log.warn("Unable to release the outbox worker lock", e);
                }
            }
        }
    }

    public int dispatchPendingEvents() {
        OutboxEventMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return 0;
        }
        List<OutboxEventHandler> handlers = handlersProvider.getIfAvailable(List::of);
        int dispatched = 0;
        for (OutboxEventRecordView row : mapper.findPending()) {
            Long tenantId = row.tenantId();
            var evt = new OutboxEventPayload(row.eventId(), row.eventType(), row.aggregateType(),
                    row.aggregateId(), tenantId, row.payloadJson());
            List<OutboxEventHandler> matched = handlers.stream()
                    .filter(h -> row.eventType().equalsIgnoreCase(h.supportedEventType()))
                    .toList();
            try (TenantAccessContextHolder.Scope ignored = tenantId != null && tenantId > 0
                    ? TenantAccessContextHolder.openTenant(tenantId, "tenant-" + tenantId, 0L)
                    : TenantAccessContextHolder.openSystemBypass(
                            TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE,
                            "Platform Outbox worker dispatch")) {
                if (matched.isEmpty()) throw new IllegalStateException("未注册该事件的处理器: " + row.eventType());
                for (OutboxEventHandler h : matched) {
                    h.handle(evt);
                }
                mapper.markSent(row.id());
                dispatched++;
            } catch (Exception ex) {
                int nextRetry = row.retryCount() + 1;
                String nextStatus = nextRetry >= row.maxRetries() ? "DEAD_LETTER" : "FAILED";
                long backoffSec = Math.min(300L, (long) Math.pow(2, nextRetry) * 5L);
                String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                if (err.length() > 480) {
                    err = err.substring(0, 480);
                }
                mapper.markFailure(row.id(), nextStatus, nextRetry, LocalDateTime.now().plusSeconds(backoffSec), err);
                log.warn("[Outbox] 事件 {} 投递失败 (retry={}/{} -> {}): {}", row.eventId(), nextRetry, row.maxRetries(), nextStatus, err);
            }
        }
        return dispatched;
    }

    public List<OutboxEventRecordView> listRecentEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return mapperProvider.getObject().findRecent(safeLimit);
    }

    public int retryEvent(long id) {
        if (mapperProvider.getObject().reschedule(id) == 0) {
            return 0;
        }
        return dispatchPendingEvents();
    }
}
