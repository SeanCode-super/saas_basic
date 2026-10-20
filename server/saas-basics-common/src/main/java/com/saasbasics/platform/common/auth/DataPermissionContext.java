package com.saasbasics.platform.common.auth;

/**
 * 旗舰级数据权限当前线程上下文
 */
public final class DataPermissionContext {

    private static final ThreadLocal<String> RESOURCE_CODE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<DataPermissionSqlSpec> SQL_SPEC_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<DataScope> DATA_SCOPE_HOLDER = new ThreadLocal<>();

    private DataPermissionContext() {
    }

    public static void setResourceCode(String resourceCode) {
        RESOURCE_CODE_HOLDER.set(resourceCode);
    }

    public static String getResourceCode() {
        return RESOURCE_CODE_HOLDER.get();
    }

    public static void setSqlSpec(DataPermissionSqlSpec spec) {
        SQL_SPEC_HOLDER.set(spec);
    }

    public static DataPermissionSqlSpec getSqlSpec() {
        return SQL_SPEC_HOLDER.get();
    }

    public static void setDataScope(DataScope dataScope) {
        DATA_SCOPE_HOLDER.set(dataScope);
    }

    public static DataScope getDataScope() {
        return DATA_SCOPE_HOLDER.get();
    }

    public static void clear() {
        RESOURCE_CODE_HOLDER.remove();
        SQL_SPEC_HOLDER.remove();
        DATA_SCOPE_HOLDER.remove();
    }
}
