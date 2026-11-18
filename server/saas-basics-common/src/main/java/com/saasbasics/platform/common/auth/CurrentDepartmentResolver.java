package com.saasbasics.platform.common.auth;

/**
 * 解析当前租户与用户的归属部门 ID，供 MyBatis-Plus 自动填充及业务创建链路统一落盘 dept_id。
 */
public interface CurrentDepartmentResolver {

    /**
     * 解析指定租户用户在组织架构中的主归属部门 ID，若未绑定员工或无归属部门则返回 null。
     */
    Long resolveDepartmentId(Long tenantId, Long userId);
}
