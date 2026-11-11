package com.saasbasics.platform.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口防刷与租户级滑动窗口限流注解。
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
     * 频次限制（默认每秒最大请求数；若设置 permits &gt; 0 则优先使用 permits + windowSeconds）
     */
    int permitsPerSecond() default 100;

    /**
     * 自定义窗口内最大许可数（为 0 时回退使用 permitsPerSecond）
     */
    int permits() default 0;

    /**
     * 滑动时间窗口长度（秒），默认 1 秒
     */
    int windowSeconds() default 1;

    /**
     * 限流触发时的提示消息
     */
    String message() default "当前请求频次过高，触发租户级限流保护，请稍后再试";
}
