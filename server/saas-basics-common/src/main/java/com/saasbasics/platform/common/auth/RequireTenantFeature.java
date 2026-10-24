package com.saasbasics.platform.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明调用该接口或控制器所需的租户端应用特性/套餐权限 (Tier-1 Tenant Entitlement)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireTenantFeature {

    /**
     * 所需租户应用/特性编码 (如 "workflow", "crm", "erp", "scheduler", "file" 等)
     */
    String value();

    /**
     * 未开通时的友好提示
     */
    String message() default "当前租户未开通该应用功能，请联系平台管理员升级套餐";
}
