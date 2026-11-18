package com.saasbasics.platform.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口或控制器仅限平台超级租户（platform / tenant_id = 1）特权调用，
 * 用于严格锁死宿主机 DevOps、物理机监控、全局传输密钥及全局慢 SQL 运维接口。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePlatformTenant {
}
