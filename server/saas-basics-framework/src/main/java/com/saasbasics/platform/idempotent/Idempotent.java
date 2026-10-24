package com.saasbasics.platform.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式业务幂等与防重复提交注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 业务唯一标识或自定义 Key 前缀。若为空则依据用户 ID、方法名及参数哈希自动生成。
     */
    String key() default "";

    /**
     * 幂等窗口期（毫秒），在该时间窗口内相同特征的请求将被拦截，默认 3000ms
     */
    long expireMillis() default 3000L;

    /**
     * 触发重复提交拦截时的业务提示消息
     */
    String message() default "操作正在处理中，请勿重复提交";
}
