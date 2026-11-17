package com.saasbasics.platform.ratelimit;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimitAspect {

    private final SlidingWindowRateLimiter rateLimiter;

    public RateLimitAspect(SlidingWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
        HttpServletResponse response = attrs != null ? attrs.getResponse() : null;

        String limitKey = buildLimitKey(rateLimit.type(), methodName, request);
        int maxPermits = rateLimit.permits() > 0 ? rateLimit.permits() : rateLimit.permitsPerSecond();
        int windowSeconds = Math.max(1, rateLimit.windowSeconds());

        RateLimitDecision decision = rateLimiter.tryAcquire(limitKey, maxPermits, windowSeconds, rateLimit.type().name());
        rateLimiter.applyResponseHeaders(response, decision);

        if (!decision.allowed()) {
            throw new BizException("RATE_LIMIT_EXCEEDED", rateLimit.message());
        }

        return joinPoint.proceed();
    }

    private String buildLimitKey(RateLimitType type, String methodName, HttpServletRequest request) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 0L;
        String ip = rateLimiter.extractClientIp(request);

        return switch (type) {
            case TENANT -> "tenant:" + tenantId + ":" + methodName;
            case USER -> "user:" + userId + ":" + methodName;
            case IP -> "ip:" + ip + ":" + methodName;
            case GLOBAL -> "global:" + methodName;
        };
    }
}
