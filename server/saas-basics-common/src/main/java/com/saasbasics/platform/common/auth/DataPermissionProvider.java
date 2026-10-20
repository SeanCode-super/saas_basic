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
}
