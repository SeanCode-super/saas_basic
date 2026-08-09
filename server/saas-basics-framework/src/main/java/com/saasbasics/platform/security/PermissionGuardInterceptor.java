package com.saasbasics.platform.security;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionGuardInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
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

        List<String> permissions = principal.permissions();
        if (permissions == null || !permissions.contains(requirePermission.value())) {
            throw new BizException("AUTH_FORBIDDEN", "Permission denied: " + requirePermission.value());
        }
        return true;
    }
}
