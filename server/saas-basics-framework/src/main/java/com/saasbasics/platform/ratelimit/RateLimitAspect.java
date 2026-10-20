package com.saasbasics.platform.ratelimit;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimitAspect {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<String, SlidingWindowCounter> localCounters = new ConcurrentHashMap<>();

    public RateLimitAspect(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        String limitKey = buildLimitKey(rateLimit.type(), methodName);
        int maxPermits = rateLimit.permitsPerSecond();

        boolean allowed = tryAcquire(limitKey, maxPermits);
        if (!allowed) {
            throw new BizException("RATE_LIMIT_EXCEEDED", rateLimit.message());
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletResponse response = attrs.getResponse();
            if (response != null) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(maxPermits));
            }
        }

        return joinPoint.proceed();
    }

    private boolean tryAcquire(String key, int maxPermits) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                Long current = redis.opsForValue().increment("ratelimit:" + key);
                if (current != null && current == 1) {
                    redis.expire("ratelimit:" + key, Duration.ofSeconds(1));
                }
                return current != null && current <= maxPermits;
            } catch (Exception ignored) {
                // Redis 偶发波动时平滑降级至本地滑动计数器
            }
        }

        long currentSecond = System.currentTimeMillis() / 1000;
        String localKey = key + ":" + currentSecond;
        SlidingWindowCounter counter = localCounters.computeIfAbsent(localKey, k -> new SlidingWindowCounter(currentSecond));

        // 清理过期的历史秒级计数器
        if (localCounters.size() > 500) {
            localCounters.entrySet().removeIf(e -> e.getValue().second < currentSecond - 5);
        }

        return counter.count.incrementAndGet() <= maxPermits;
    }

    private String buildLimitKey(RateLimitType type, String methodName) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 0L;

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String ip = "127.0.0.1";
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            ip = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
        }

        return switch (type) {
            case TENANT -> "tenant:" + tenantId + ":" + methodName;
            case USER -> "user:" + userId + ":" + methodName;
            case IP -> "ip:" + ip + ":" + methodName;
            case GLOBAL -> "global:" + methodName;
        };
    }

    private static class SlidingWindowCounter {
        final long second;
        final AtomicInteger count = new AtomicInteger(0);

        SlidingWindowCounter(long second) {
            this.second = second;
        }
    }
}
