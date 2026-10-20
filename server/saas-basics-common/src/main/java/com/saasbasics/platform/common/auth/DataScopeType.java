package com.saasbasics.platform.common.auth;

/**
 * 旗舰级数据权限范围类型枚举
 */
public enum DataScopeType {

    /** 全部数据权限（无组织或人员范围限制） */
    ALL,

    /** 本组织主体及全部下级组织主体 */
    ORG_AND_CHILDREN,

    /** 仅本组织主体 */
    ORG_ONLY,

    /** 本组织单元/部门及所有递归子部门 */
    DEPT_AND_CHILDREN,

    /** 仅本组织单元/部门 */
    DEPT_ONLY,

    /** 本人及下属任职人员 */
    SELF_AND_SUBORDINATES,

    /** 仅本人经办或所属数据 */
    SELF,

    /** 自定义组织主体或组织单元范围 */
    CUSTOM
}
