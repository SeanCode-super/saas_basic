package com.saasbasics.platform.security;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.auth.RequireTenantFeature;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import com.saasbasics.platform.common.tenant.TenantAppAccess;
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

    private final ObjectProvider<TenantAppAccess> installations;

    public TenantFeatureGuardInterceptor(ObjectProvider<TenantFeatureProvider> tenantFeatureProvider,
                                         ObjectProvider<TenantAppAccess> installations) {
        this.installations = installations;
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
        if (annotation.installedRevision() > 0) {
            TenantAppAccess access = installations.getIfAvailable();
            if (principal == null || principal.tenantId() == null || access == null
                    || !access.isInstalled(principal.tenantId(), annotation.value(), annotation.installedRevision())) {
                throw new BizException("TENANT_FEATURE_NOT_SUBSCRIBED", annotation.message());
            }
            return true;
        }
        if (principal == null || principal.tenantId() == null || principal.tenantId() <= 0) {
            throw new BizException("AUTH_FORBIDDEN", "缺少有效租户身份");
        }
        TenantFeatureProvider provider = tenantFeatureProvider.getIfAvailable();
        if (provider == null || !provider.hasFeature(principal.tenantId(), annotation.value())) {
            throw new BizException("TENANT_FEATURE_NOT_SUBSCRIBED", annotation.message());
        }
        return true;
    }
}
