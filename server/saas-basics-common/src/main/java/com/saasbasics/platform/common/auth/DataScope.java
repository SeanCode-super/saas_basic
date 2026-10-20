package com.saasbasics.platform.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 旗舰级声明式数据权限注解
 * 可标注在 Mapper 方法、接口类、Service 或 Controller 上
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface DataScope {

    /**
     * 关联的业务资源标识，例如 "organization:org-unit"、"iam:user"
     */
    String resource() default "";

    /**
     * 目标表别名，如果 SQL 查询涉及多表 JOIN，可指定主表别名（例如 "t"）
     */
    String tableAlias() default "";

    /**
     * 组织主体过滤字段名，默认 "organization_id"
     */
    String orgColumn() default "organization_id";

    /**
     * 组织单元/部门过滤字段名，默认 "org_unit_id"
     */
    String unitColumn() default "org_unit_id";

    /**
     * 数据归属人/经办人过滤字段名，默认 "created_by"
     */
    String userColumn() default "created_by";

    /**
     * 是否忽略数据权限过滤（例如特权调用、内部聚合统计）
     */
    boolean ignore() default false;
}
