package com.saasbasics.platform.repeat;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防重复提交切面
 */
@Aspect
@Component
public class RepeatSubmitAspect {

    private static final Map<String, Long> SUBMIT_CACHE = new ConcurrentHashMap<>();
    private static final int CLEANUP_THRESHOLD = 2000;

    @Before("@annotation(repeatSubmit)")
    public void doBefore(JoinPoint point, RepeatSubmit repeatSubmit) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        AuthPrincipal principal = AuthContext.get();
        String userKey = (principal != null && principal.userId() != null)
                ? "user:" + principal.tenantId() + ":" + principal.userId()
                : "ip:" + getClientIp(request);

        String path = request.getRequestURI();
        String methodName = point.getSignature().toShortString();
        String argsHash = Arrays.deepHashCode(point.getArgs()) + "";

        String lockKey = userKey + ":" + path + ":" + methodName + ":" + argsHash;
        long currentTime = System.currentTimeMillis();
        int interval = repeatSubmit.interval();

        Long lastTime = SUBMIT_CACHE.get(lockKey);
        if (lastTime != null && (currentTime - lastTime) < interval) {
            throw new BizException("REPEAT_SUBMIT_BLOCKED", repeatSubmit.message());
        }

        SUBMIT_CACHE.put(lockKey, currentTime);

        // 定期清理过期键，防止内存泄漏
        if (SUBMIT_CACHE.size() > CLEANUP_THRESHOLD) {
            SUBMIT_CACHE.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > 60000);
        }
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
