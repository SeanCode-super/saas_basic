package com.saasbasics.platform.security.transport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 控制器或接口方法级别的传输加密控制注解，可覆盖路径匹配规则。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TransportCrypto {

    Rule value() default Rule.REQUIRED;

    enum Rule {
        /**
         * 强制要求此接口加密（当全局非 OFF 时要求密文）。
         */
        REQUIRED,
        /**
         * 按客户端协商（传密文则解密并加密回传，传明文则放行）。
         */
        OPTIONAL,
        /**
         * 显式豁免此接口（始终允许明文透传，例如回调或大流下载）。
         */
        PLAIN
    }
}
