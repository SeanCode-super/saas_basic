package com.saasbasics.platform.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 分布式 + 本地平滑降级滑动窗口限流核心服务。
 * Redis 在线时通过原子 Lua ZSET 脚本精确限流；Redis 不可用时降级至有界内存滑动窗口队列。
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class SlidingWindowRateLimiter {

    private static final int MAX_LOCAL_BUCKETS = 10_000;

    private static final DefaultRedisScript<List> SLIDING_WINDOW_LUA;

    static {
        SLIDING_WINDOW_LUA = new DefaultRedisScript<>();
        SLIDING_WINDOW_LUA.setScriptText("""
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
                local limit = tonumber(ARGV[3])
                local member = ARGV[4]
                local expireSec = math.ceil(window / 1000) + 1
                redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
                local current = redis.call('ZCARD', key)
                if current >= limit then
                  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                  local resetSec = expireSec
                  if oldest and #oldest >= 2 then
                    resetSec = math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
                  end
                  return {0, current, resetSec}
                end
                redis.call('ZADD', key, now, member)
                redis.call('EXPIRE', key, expireSec)
                return {1, current + 1, expireSec}
                """);
        SLIDING_WINDOW_LUA.setResultType(List.class);
    }

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ConcurrentHashMap<String, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final AtomicLong totalAllowed = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    public SlidingWindowRateLimiter(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    public RateLimitDecision tryAcquire(String key, int maxPermits, int windowSeconds, String dimension) {
        int safeLimit = Math.max(1, maxPermits);
        int safeWindowSec = Math.max(1, windowSeconds);
        long windowMillis = safeWindowSec * 1000L;
        long now = System.currentTimeMillis();

        RateLimitDecision decision = tryAcquireRedis(key, now, windowMillis, safeLimit, safeWindowSec);
        if (decision == null) {
            decision = tryAcquireLocal(key, now, windowMillis, safeLimit, safeWindowSec);
        }

        recordMetrics(dimension, decision.allowed());
        return decision;
    }

    public void applyResponseHeaders(HttpServletResponse response, RateLimitDecision decision) {
        if (response == null || decision == null) {
            return;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(Math.max(1L, decision.resetSeconds())));
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "127.0.0.1";
    }

    public long getTotalAllowedCount() {
        return totalAllowed.get();
    }

    public long getTotalRejectedCount() {
        return totalRejected.get();
    }

    public void clearLocalWindows() {
        localWindows.clear();
    }

    @SuppressWarnings("unchecked")
    private RateLimitDecision tryAcquireRedis(String key, long now, long windowMillis, int limit, int windowSec) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            String redisKey = "ratelimit:sw:" + key;
            String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);
            List<Object> result = redis.execute(
                    SLIDING_WINDOW_LUA,
                    List.of(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    member
            );
            if (result != null && result.size() >= 3) {
                long allowedFlag = toLong(result.get(0));
                int current = (int) toLong(result.get(1));
                long resetSec = Math.max(1L, toLong(result.get(2)));
                boolean allowed = allowedFlag == 1L;
                int remaining = allowed ? Math.max(0, limit - current) : 0;
                return new RateLimitDecision(allowed, limit, remaining, resetSec);
            }
        } catch (Exception ignored) {
            // Redis 不可用或异常时自动无感降级至本地滑动窗口
        }
        return null;
    }

    private RateLimitDecision tryAcquireLocal(String key, long now, long windowMillis, int limit, int windowSec) {
        if (localWindows.size() >= MAX_LOCAL_BUCKETS) {
            localWindows.entrySet().removeIf(entry -> (now - entry.getValue().lastAccessMillis) > windowMillis);
            if (localWindows.size() >= MAX_LOCAL_BUCKETS) {
                localWindows.clear();
            }
        }

        LocalWindow bucket = localWindows.computeIfAbsent(key, k -> new LocalWindow(now));
        synchronized (bucket) {
            bucket.lastAccessMillis = now;
            while (!bucket.timestamps.isEmpty() && (now - bucket.timestamps.peekFirst()) >= windowMillis) {
                bucket.timestamps.pollFirst();
            }
            if (bucket.timestamps.size() >= limit) {
                Long oldest = bucket.timestamps.peekFirst();
                long resetMillis = oldest == null ? windowMillis : Math.max(1000L, oldest + windowMillis - now);
                long resetSec = (resetMillis + 999L) / 1000L;
                return new RateLimitDecision(false, limit, 0, Math.min(windowSec, Math.max(1L, resetSec)));
            }
            bucket.timestamps.addLast(now);
            int remaining = Math.max(0, limit - bucket.timestamps.size());
            return new RateLimitDecision(true, limit, remaining, windowSec);
        }
    }

    private void recordMetrics(String dimension, boolean allowed) {
        if (allowed) {
            totalAllowed.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
        }
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("saas_ratelimit_requests_total")
                    .description("Platform API rate limiter request outcomes")
                    .tag("dimension", dimension == null ? "GLOBAL" : dimension)
                    .tag("outcome", allowed ? "allowed" : "rejected")
                    .register(registry)
                    .increment();
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }

    private static final class LocalWindow {
        final Deque<Long> timestamps = new ArrayDeque<>();
        volatile long lastAccessMillis;

        LocalWindow(long now) {
            this.lastAccessMillis = now;
        }
    }
}
