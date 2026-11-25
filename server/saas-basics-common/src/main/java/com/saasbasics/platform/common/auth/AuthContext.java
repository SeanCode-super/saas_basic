package com.saasbasics.platform.common.auth;

import com.saasbasics.platform.common.exception.BizException;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static AuthPrincipal requirePrincipal() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null || principal.tenantId() == null || principal.userId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal;
    }

    public static Long getUserId() {
        AuthPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.userId();
    }

    public static String getTenantCode() {
        AuthPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.tenantCode();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
