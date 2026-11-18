package com.saasbasics.platform.security;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.security.SecurityBlacklistChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionGuardInterceptor implements HandlerInterceptor {

    private SecurityBlacklistChecker securityBlacklistChecker;

    @Autowired(required = false)
    public void setSecurityBlacklistChecker(SecurityBlacklistChecker securityBlacklistChecker) {
        this.securityBlacklistChecker = securityBlacklistChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AuthPrincipal currentPrincipal = AuthContext.get();
        if (securityBlacklistChecker != null && currentPrincipal != null && !currentPrincipal.isSuperAdmin()) {
            securityBlacklistChecker.assertAllowed(
                    currentPrincipal.tenantId(),
                    currentPrincipal.username(),
                    resolveClientIp(request)
            );
        }

        RequirePermission requirePermission = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequirePermission.class
        );
        if (requirePermission == null) {
            requirePermission = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RequirePermission.class
            );
        }
        if (requirePermission == null) {
            return true;
        }

        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }

        if (principal.isSuperAdmin()) {
            return true;
        }

        List<String> permissions = principal.permissions();
        if (permissions == null || (!permissions.contains("*") && !permissions.contains("*:*:*") && !permissions.contains(requirePermission.value()))) {
            throw new BizException("AUTH_FORBIDDEN", "Permission denied: " + requirePermission.value());
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "";
    }
}
