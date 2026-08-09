package com.saasbasics.platform.common.auth;

public final class DataPermissionContext {

    private static final ThreadLocal<String> RESOURCE_CODE_HOLDER = new ThreadLocal<>();

    private DataPermissionContext() {
    }

    public static void setResourceCode(String resourceCode) {
        RESOURCE_CODE_HOLDER.set(resourceCode);
    }

    public static String getResourceCode() {
        return RESOURCE_CODE_HOLDER.get();
    }

    public static void clear() {
        RESOURCE_CODE_HOLDER.remove();
    }
}
