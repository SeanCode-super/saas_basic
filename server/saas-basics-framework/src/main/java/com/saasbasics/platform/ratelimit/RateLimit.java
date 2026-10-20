package com.saasbasics.platform.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口防刷与租户级限流注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流维度类型，默认租户级限流
     */
    RateLimitType type() default RateLimitType.TENANT;

    /**
     * 频次限制（每秒允许的最大请求数，QPS）
     */
    int permitsPerSecond() default 100;

    /**
     * 限流触发时的提示消息
     */
    String message() default "当前请求频次过高，触发租户级限流保护，请稍后再试";
}
