package com.saasbasics.platform.ratelimit;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式/内存滑窗限流切面
 */
@Aspect
@Component
public class RateLimiterAspect {

    private static final Map<String, Deque<Long>> WINDOW_CACHE = new ConcurrentHashMap<>();

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        String key = buildLimitKey(request, rateLimiter, method);
        int maxCount = rateLimiter.count();
        long windowMillis = rateLimiter.time() * 1000L;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = WINDOW_CACHE.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            // 清理过期的时间戳
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMillis) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxCount) {
                throw new BizException("RATE_LIMIT_EXCEEDED", rateLimiter.message());
            }

            timestamps.addLast(now);
        }
    }

    private String buildLimitKey(HttpServletRequest request, RateLimiter rateLimiter, Method method) {
        StringBuilder sb = new StringBuilder("rate_limit:");
        if (!rateLimiter.key().isEmpty()) {
            sb.append(rateLimiter.key()).append(":");
        }
        sb.append(method.getDeclaringClass().getSimpleName()).append("#").append(method.getName()).append(":");

        switch (rateLimiter.limitType()) {
            case IP -> sb.append(getClientIp(request));
            case USER -> {
                AuthPrincipal principal = AuthContext.get();
                if (principal != null && principal.userId() != null) {
                    sb.append("u_").append(principal.userId());
                } else {
                    sb.append(getClientIp(request));
                }
            }
            default -> sb.append("global");
        }
        return sb.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
