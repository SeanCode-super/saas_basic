package com.saasbasics.platform.common.tenant;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import java.util.List;

public final class TenantAccessContextHolder {

    private static final ThreadLocal<TenantAccessContext> HOLDER = new ThreadLocal<>();

    private TenantAccessContextHolder() {
    }

    public static TenantAccessContext current() {
        return HOLDER.get();
    }

    public static Long requiredTenantId() {
        TenantAccessContext context = HOLDER.get();
        if (context == null || context.mode() != TenantAccessContext.Mode.TENANT) {
            throw new BizException("TENANT_CONTEXT_REQUIRED", "An authenticated tenant context is required");
        }
        return context.tenantId();
    }

    public static Scope openAuthenticated(AuthPrincipal principal) {
        if (principal == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return openTenant(principal.tenantId(), principal.tenantCode(), principal.userId());
    }

    public static Scope openTenant(Long tenantId, String tenantCode, Long actorId) {
        return replace(TenantAccessContext.tenant(tenantId, tenantCode, actorId));
    }

    public static Scope openSystemBypass(TenantAccessContext.BypassOperation operation, String reason) {
        return replace(TenantAccessContext.bypass(
                TenantAccessContext.Mode.SYSTEM_BYPASS,
                0L,
                operation,
                reason
        ));
    }

    public static Scope openPlatformBypass(String requiredPermission,
                                           TenantAccessContext.BypassOperation operation,
                                           String reason) {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !"PLATFORM".equalsIgnoreCase(principal.userType())) {
            throw new BizException("TENANT_BYPASS_FORBIDDEN", "Platform identity is required for tenant bypass");
        }
        List<String> permissions = principal.permissions();
        if (requiredPermission == null || requiredPermission.isBlank()
                || permissions == null || !permissions.contains(requiredPermission)) {
            throw new BizException("TENANT_BYPASS_FORBIDDEN", "Explicit tenant bypass permission is required");
        }
        return replace(TenantAccessContext.bypass(
                TenantAccessContext.Mode.PLATFORM_BYPASS,
                principal.userId(),
                operation,
                reason
        ));
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static Scope replace(TenantAccessContext next) {
        TenantAccessContext previous = HOLDER.get();
        HOLDER.set(next);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {

        private final TenantAccessContext previous;
        private boolean closed;

        private Scope(TenantAccessContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
