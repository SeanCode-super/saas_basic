package com.saasbasics.platform.ratelimit;

import java.lang.annotation.*;

/**
 * 分布式限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 限流时间窗口，单位秒
     */
    int time() default 60;

    /**
     * 时间窗口内最大允许访问次数
     */
    int count() default 100;

    /**
     * 限流类型（默认全局、IP、用户）
     */
    LimitType limitType() default LimitType.IP;

    /**
     * 提示消息
     */
    String message() default "访问过于频繁，已被限流保护，请稍后再试";
}
