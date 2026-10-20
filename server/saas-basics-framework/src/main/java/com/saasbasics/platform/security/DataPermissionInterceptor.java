package com.saasbasics.platform.security;

import com.saasbasics.platform.common.auth.DataPermissionContext;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.common.auth.RequireDataPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DataPermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            DataPermissionContext.clear();
            return true;
        }

        // 1. 优先提取 @DataScope 注解
        DataScope dataScope = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                DataScope.class
        );
        if (dataScope == null) {
            dataScope = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    DataScope.class
            );
        }

        if (dataScope != null) {
            DataPermissionContext.setDataScope(dataScope);
            if (!dataScope.resource().isBlank()) {
                DataPermissionContext.setResourceCode(dataScope.resource());
            }
            return true;
        }

        // 2. 兼容提取 @RequireDataPermission 注解
        RequireDataPermission requireDataPermission = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireDataPermission.class
        );
        if (requireDataPermission == null) {
            requireDataPermission = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RequireDataPermission.class
            );
        }

        if (requireDataPermission == null) {
            DataPermissionContext.clear();
            return true;
        }

        DataPermissionContext.setResourceCode(requireDataPermission.value());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataPermissionContext.clear();
    }
}
