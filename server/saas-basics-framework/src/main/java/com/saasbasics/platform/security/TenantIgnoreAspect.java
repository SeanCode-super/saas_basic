package com.saasbasics.platform.security;

import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantIgnore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 处理 @TenantIgnore 注解的 AOP 切面。
 * 确保被标注的方法在执行期间自动开启 SYSTEM_BYPASS，并在执行完毕后安全恢复原上下文。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantIgnoreAspect {

    @Around("@annotation(com.saasbasics.platform.common.tenant.TenantIgnore) || @within(com.saasbasics.platform.common.tenant.TenantIgnore)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String reason = resolveReason(joinPoint);
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE, reason)) {
            return joinPoint.proceed();
        }
    }

    private String resolveReason(ProceedingJoinPoint joinPoint) {
        if (joinPoint.getSignature() instanceof MethodSignature signature) {
            Method method = signature.getMethod();
            TenantIgnore methodAnnotation = method.getAnnotation(TenantIgnore.class);
            if (methodAnnotation != null && !methodAnnotation.reason().isBlank()) {
                return methodAnnotation.reason();
            }
            TenantIgnore classAnnotation = method.getDeclaringClass().getAnnotation(TenantIgnore.class);
            if (classAnnotation != null && !classAnnotation.reason().isBlank()) {
                return classAnnotation.reason();
            }
        }
        return "Executed under @TenantIgnore";
    }
}
