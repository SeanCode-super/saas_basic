package com.saasbasics.platform.common.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法或类忽略多租户隔离限制（自动以 SYSTEM_BYPASS 模式执行）。
 * 适用于跨租户定时任务、平台初始化、全局系统配置读取等场景。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantIgnore {

    /**
     * 忽略租户隔离的原因/业务说明
     */
    String reason() default "Annotated with @TenantIgnore";
}
