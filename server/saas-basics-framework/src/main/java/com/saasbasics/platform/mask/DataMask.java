package com.saasbasics.platform.mask;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.saasbasics.platform.common.mask.DataMaskType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段数据脱敏注解。
 * 作用于 DTO / VO 字段上，在 Jackson 序列化输出为 JSON 时自动打码（如手机号、身份证、邮箱等）。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DataMaskJsonSerializer.class)
public @interface DataMask {

    /**
     * 脱敏规则类型
     */
    DataMaskType type() default DataMaskType.CUSTOM;

    /**
     * 前缀保留位数 (type=CUSTOM 时生效)
     */
    int prefixKeep() default 1;

    /**
     * 后缀保留位数 (type=CUSTOM 时生效)
     */
    int suffixKeep() default 1;

    /**
     * 替换掩码字符
     */
    char maskChar() default '*';

    /**
     * 豁免权限标识（若当前登录用户拥有此权限，则返回明文不脱敏）
     */
    String unmaskPermission() default "data:unmask";
}
