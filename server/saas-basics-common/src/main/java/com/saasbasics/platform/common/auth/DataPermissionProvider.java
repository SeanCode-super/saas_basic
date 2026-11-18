package com.saasbasics.platform.common.auth;

/**
 * 数据权限规约解析提供者 SPI
 */
public interface DataPermissionProvider {

    /**
     * 根据当前用户会话及任职上下文，解析针对指定业务资源的数据权限规约
     *
     * @param resourceCode 业务资源标识，如 "organization:org-unit"
     * @return 经过多角色并集求解后的数据权限规约
     */
    DataPermissionSqlSpec resolveDataPermission(String resourceCode);

    /**
     * 针对异步后台任务等无 HTTP 会话线程，按显式租户与操作人主体解析数据权限规约
     */
    default DataPermissionSqlSpec resolveDataPermissionForUser(Long tenantId, Long userId, String resourceCode) {
        return resolveDataPermission(resourceCode);
    }
}
