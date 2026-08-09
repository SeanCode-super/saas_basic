package com.saasbasics.platform.common.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> TENANT_CODE_HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantCode(String tenantCode) {
        TENANT_CODE_HOLDER.set(tenantCode);
    }

    public static String getTenantCode() {
        return TENANT_CODE_HOLDER.get();
    }

    public static void clear() {
        TENANT_CODE_HOLDER.remove();
    }
}
