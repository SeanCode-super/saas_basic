package com.saasbasics.platform.repeat;

import java.lang.annotation.*;

/**
 * 自定义注解：防止表单/接口重复提交
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /**
     * 间隔时间(毫秒)，小于此时间视为重复提交
     */
    int interval() default 3000;

    /**
     * 提示消息
     */
    String message() default "操作过于频繁，请稍候再试";
}
