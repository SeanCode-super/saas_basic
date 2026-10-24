package com.saasbasics.platform.security;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户应用套餐权限守卫拦截器 (Tier-1 Tenant Feature Entitlement Guard)
 */
@Component
public class TenantFeatureGuardInterceptor implements HandlerInterceptor {

    private final ObjectProvider<TenantFeatureProvider> tenantFeatureProvider;

    public TenantFeatureGuardInterceptor(ObjectProvider<TenantFeatureProvider> tenantFeatureProvider) {
        this.tenantFeatureProvider = tenantFeatureProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireTenantFeature annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireTenantFeature.class
        );
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RequireTenantFeature.class
            );
        }
        if (annotation == null) {
            return true;
        }

        AuthPrincipal principal = AuthContext.get();
        if (principal == null) {
            return true;
        }

        // 平台超管 (Tenant 0 或 1) 直通
        if (principal.isSuperAdmin() && (principal.tenantId() == null || principal.tenantId() <= 1L)) {
            return true;
        }

        Long tenantId = principal.tenantId();
        TenantFeatureProvider provider = tenantFeatureProvider.getIfAvailable();
        if (provider != null && tenantId != null) {
            boolean hasFeature = provider.hasFeature(tenantId, annotation.value());
            if (!hasFeature) {
                throw new BizException("TENANT_FEATURE_NOT_SUBSCRIBED", annotation.message());
            }
        }
        return true;
    }
}
