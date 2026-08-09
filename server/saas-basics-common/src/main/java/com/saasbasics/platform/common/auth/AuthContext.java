package com.saasbasics.platform.common.auth;

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
