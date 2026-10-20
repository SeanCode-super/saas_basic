package com.saasbasics.platform.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 工业级双模弹性缓存服务（Redis + DB 兜底防雪崩）
 * 提供强健容错能力：Redis 异常或离线时自动透明降级至数据库直查，业务零感知。
 */
@Service
public class SystemCacheService {

    private static final Logger log = LoggerFactory.getLogger(SystemCacheService.class);

    public static final String KEY_PREFIX = "saas:";
    public static final String KEY_DEPT_TREE = "saas:org:dept_tree:%d:%d";
    public static final String KEY_USER_PERMS = "saas:iam:perms:%d:%d";
    public static final String KEY_MENU_TREE = "saas:iam:menus:%d";
    public static final String KEY_DATA_RULES = "saas:iam:data_rules:%d:%s";

    private static final long DEFAULT_TTL_SECONDS = 7200; // 2 小时

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    public SystemCacheService(ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public boolean isRedisAvailable() {
        RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(template.execute((org.springframework.data.redis.core.RedisCallback<Boolean>) connection -> !connection.isClosed()));
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, long ttlSeconds, Supplier<T> fallback) {
        RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
        if (template != null) {
            try {
                Object cached = template.opsForValue().get(key);
                if (cached != null) {
                    return (T) cached;
                }
            } catch (Exception ex) {
                log.warn("Redis 读取缓存异常，自动降级至底层数据库查询: key={}, error={}", key, ex.getMessage());
            }
        }

        T result = fallback.get();
        if (result != null && template != null) {
            try {
                template.opsForValue().set(key, result, ttlSeconds, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.warn("Redis 写入缓存异常: key={}, error={}", key, ex.getMessage());
            }
        }
        return result;
    }

    public void evict(String key) {
        RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
        if (template != null) {
            try {
                template.delete(key);
            } catch (Exception ex) {
                log.warn("Redis 删除缓存异常: key={}, error={}", key, ex.getMessage());
            }
        }
    }

    public void evictByPattern(String pattern) {
        RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
        if (template != null) {
            try {
                Set<String> keys = template.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    template.delete(keys);
                    log.info("Redis 批量失效缓存完成: pattern={}, keysCount={}", pattern, keys.size());
                }
            } catch (Exception ex) {
                log.warn("Redis 批量清理缓存异常: pattern={}, error={}", pattern, ex.getMessage());
            }
        }
    }

    /**
     * 一键重载/清空系统全部缓存（供管理员后台一键刷新或 DBA 直接更新 SQL 后重新加载使用）
     *
     * @return 清理并刷新的缓存 Key 数量
     */
    public int reloadAllSystemCaches() {
        RedisTemplate<String, Object> template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            return 0;
        }
        try {
            Set<String> keys = template.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = template.delete(keys);
                log.info("管理员触发全局系统缓存重新加载，已成功清理并失效旧缓存项: count={}", deletedCount);
                return deletedCount != null ? deletedCount.intValue() : keys.size();
            }
            return 0;
        } catch (Exception ex) {
            log.error("Redis 全局缓存清理重载失败: {}", ex.getMessage());
            return 0;
        }
    }
}
