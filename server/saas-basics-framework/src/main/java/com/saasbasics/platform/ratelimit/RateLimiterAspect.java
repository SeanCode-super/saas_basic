package com.saasbasics.platform.ratelimit;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 分布式/内存滑动窗口限流切面
 */
@Aspect
@Component
public class RateLimiterAspect {

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;

    public RateLimiterAspect(SlidingWindowRateLimiter slidingWindowRateLimiter) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
    }

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        String key = buildLimitKey(request, rateLimiter, method);
        RateLimitDecision decision = slidingWindowRateLimiter.tryAcquire(
                key,
                rateLimiter.count(),
                rateLimiter.time(),
                rateLimiter.limitType().name()
        );
        slidingWindowRateLimiter.applyResponseHeaders(response, decision);

        if (!decision.allowed()) {
            throw new BizException("RATE_LIMIT_EXCEEDED", rateLimiter.message());
        }
    }

    private String buildLimitKey(HttpServletRequest request, RateLimiter rateLimiter, Method method) {
        StringBuilder sb = new StringBuilder("anno:");
        if (!rateLimiter.key().isEmpty()) {
            sb.append(rateLimiter.key()).append(":");
        }
        sb.append(method.getDeclaringClass().getSimpleName()).append("#").append(method.getName()).append(":");

        switch (rateLimiter.limitType()) {
            case IP -> sb.append(slidingWindowRateLimiter.extractClientIp(request));
            case USER -> {
                AuthPrincipal principal = AuthContext.get();
                if (principal != null && principal.userId() != null) {
                    sb.append("u_").append(principal.userId());
                } else {
                    sb.append(slidingWindowRateLimiter.extractClientIp(request));
                }
            }
            default -> sb.append("global");
        }
        return sb.toString();
    }
}
