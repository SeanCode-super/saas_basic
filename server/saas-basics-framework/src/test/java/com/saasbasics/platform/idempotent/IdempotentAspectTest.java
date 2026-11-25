package com.saasbasics.platform.idempotent;

import com.saasbasics.platform.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("分布式业务幂等与防重切面单元测试")
class IdempotentAspectTest {

    @Test
    @DisplayName("首次请求正常放行并返回结果")
    void shouldProceedWhenFirstCall() throws Throwable {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        IdempotentAspect aspect = new IdempotentAspect(beanFactory.getBeanProvider(StringRedisTemplate.class));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method sampleMethod = SampleService.class.getDeclaredMethod("doAction", String.class);
        Idempotent idempotent = sampleMethod.getAnnotation(Idempotent.class);

        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(sampleMethod);
        when(pjp.getArgs()).thenReturn(new Object[]{"req-001"});
        when(pjp.proceed()).thenReturn("SUCCESS");

        Object result = aspect.around(pjp, idempotent);
        assertThat(result).isEqualTo("SUCCESS");
        verify(pjp, times(1)).proceed();
    }

    @Test
    @DisplayName("在幂等窗口内重复请求时必须抛出DUPLICATE_REQUEST异常")
    void shouldRejectDuplicateCallWithinWindow() throws Throwable {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        IdempotentAspect aspect = new IdempotentAspect(beanFactory.getBeanProvider(StringRedisTemplate.class));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method sampleMethod = SampleService.class.getDeclaredMethod("doAction", String.class);
        Idempotent idempotent = sampleMethod.getAnnotation(Idempotent.class);

        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(sampleMethod);
        when(pjp.getArgs()).thenReturn(new Object[]{"req-duplicate-check"});
        when(pjp.proceed()).thenReturn("SUCCESS");

        // 第一次成功
        aspect.around(pjp, idempotent);

        // 第二次在窗口期内重复触发，必须抛出 BizException
        assertThatThrownBy(() -> aspect.around(pjp, idempotent))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("操作正在处理中");
    }

    static class SampleService {
        @Idempotent(key = "sample:order", expireMillis = 5000L)
        public String doAction(String reqId) {
            return "OK";
        }
    }
}
