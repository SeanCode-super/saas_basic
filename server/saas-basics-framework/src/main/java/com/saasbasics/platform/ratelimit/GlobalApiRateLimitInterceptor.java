package com.saasbasics.platform.ratelimit;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全局 API 网关多维度限流拦截器：按租户 RPM、AI 接口严格配额与客户端 IP 防护进行实时限流。
 */
@Component
public class GlobalApiRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final SlidingWindowRateLimiter rateLimiter;

    public GlobalApiRateLimitInterceptor(RateLimitProperties properties, SlidingWindowRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.startsWith("/api/health") || uri.startsWith("/actuator")) {
            return true;
        }

        int window = Math.max(1, properties.getWindowSeconds());
        AuthPrincipal principal = AuthContext.get();
        if (principal != null && principal.tenantId() != null) {
            boolean isAiRoute = uri.startsWith("/api/ai");
            int limit = isAiRoute ? properties.getAiTenantRpm() : properties.getTenantRpm();
            String scope = isAiRoute ? "GATEWAY_AI_TENANT" : "GATEWAY_TENANT";
            String key = scope + ":t_" + principal.tenantId();
            RateLimitDecision decision = rateLimiter.tryAcquire(key, limit, window, scope);
            rateLimiter.applyResponseHeaders(response, decision);
            if (!decision.allowed()) {
                throw new BizException(
                        "RATE_LIMIT_EXCEEDED",
                        isAiRoute
                                ? "AI 网关调用过于频繁，已触发租户级智能限流保护（上限 " + limit + " 次/" + window + "秒）"
                                : "当前租户请求速率超出系统保护上限（" + limit + " 次/" + window + "秒），请稍后再试"
                );
            }
            return true;
        }

        // Anonymous / public endpoint IP-level burst guard
        String ip = rateLimiter.extractClientIp(request);
        RateLimitDecision decision = rateLimiter.tryAcquire(
                "GATEWAY_IP:" + ip,
                properties.getIpRpm(),
                window,
                "GATEWAY_IP"
        );
        rateLimiter.applyResponseHeaders(response, decision);
        if (!decision.allowed()) {
            throw new BizException("RATE_LIMIT_EXCEEDED", "当前 IP 访问频次过高，已触发网关限流保护");
        }
        return true;
    }
}
