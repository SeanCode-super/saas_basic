package com.saasbasics.platform.idempotent;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class IdempotentAspect {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<String, Long> localLockStore = new ConcurrentHashMap<>();

    public IdempotentAspect(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String lockKey = buildLockKey(joinPoint, method, idempotent);
        long expireMillis = idempotent.expireMillis();

        boolean locked = tryLock(lockKey, expireMillis);
        if (!locked) {
            throw new BizException("DUPLICATE_REQUEST", idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            // 如果执行业务时抛出异常且不是重复提交异常，释放锁允许重试
            releaseLock(lockKey);
            throw t;
        }
    }

    private boolean tryLock(String lockKey, long expireMillis) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                Boolean success = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMillis(expireMillis));
                if (success != null) {
                    return success;
                }
            } catch (Exception ignored) {
                // Redis 波动时平滑降级至本地内存锁
            }
        }

        long now = System.currentTimeMillis();
        long expireAt = now + expireMillis;

        // 清理本地锁表中已过期的键
        if (localLockStore.size() > 500) {
            localLockStore.entrySet().removeIf(e -> e.getValue() < now);
        }

        Long existingExpireAt = localLockStore.putIfAbsent(lockKey, expireAt);
        if (existingExpireAt == null) {
            return true;
        }

        if (existingExpireAt < now) {
            // 已过期，竞争更新
            return localLockStore.replace(lockKey, existingExpireAt, expireAt);
        }

        return false;
    }

    private void releaseLock(String lockKey) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(lockKey);
            } catch (Exception ignored) {
            }
        }
        localLockStore.remove(lockKey);
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, Method method, Idempotent idempotent) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 0L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 0L;

        String customKey = idempotent.key().trim();
        String methodIdentifier = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        String argsString = Arrays.deepToString(joinPoint.getArgs());

        String rawContent = "tenant:" + tenantId + ":user:" + userId + ":method:" + methodIdentifier + ":args:" + argsString;
        String hashed = sha256Hex(rawContent);

        if (!customKey.isEmpty()) {
            return "idempotent:" + customKey + ":" + tenantId + ":" + userId + ":" + hashed;
        }
        return "idempotent:" + hashed;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }
}
