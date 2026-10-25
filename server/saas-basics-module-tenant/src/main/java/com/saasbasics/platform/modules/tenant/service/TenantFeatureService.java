package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantFeatureService implements TenantFeatureProvider {

    private static final Logger log = LoggerFactory.getLogger(TenantFeatureService.class);
    private static final String REDIS_KEY_PREFIX = "tenant:features:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final long LOCAL_TTL_MILLIS = 300_000L; // 5 minutes

    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final ObjectProvider<TenantPackageMapper> tenantPackageMapperProvider;
    private final ObjectProvider<TenantAppMapper> tenantAppMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    private final Map<Long, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    private record LocalCacheEntry(Set<String> features, long expireAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    public TenantFeatureService(ObjectProvider<TenantMapper> tenantMapperProvider,
                                ObjectProvider<TenantPackageMapper> tenantPackageMapperProvider,
                                ObjectProvider<TenantAppMapper> tenantAppMapperProvider,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                ObjectMapper objectMapper) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.tenantPackageMapperProvider = tenantPackageMapperProvider;
        this.tenantAppMapperProvider = tenantAppMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean hasFeature(Long tenantId, String feature) {
        if (tenantId == null || tenantId <= 1L) {
            return true;
        }
        if (feature == null || feature.isBlank() || "dashboard".equalsIgnoreCase(feature.trim())) {
            return true;
        }

        Set<String> effectiveFeatures = getEffectiveFeatures(tenantId);
        if (effectiveFeatures.contains("*")) {
            return true;
        }

        String target = feature.trim().toLowerCase(Locale.ROOT);
        for (String eff : effectiveFeatures) {
            String low = eff.toLowerCase(Locale.ROOT);
            if ("*".equals(low)) {
                return true;
            }
            if (low.equals(target)) {
                return true;
            }
            if (low.startsWith("saas." + target + ".") || low.equals("saas." + target)) {
                return true;
            }
            if (target.startsWith("saas." + low + ".") || target.equals("saas." + low)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getEffectiveFeatures(Long tenantId) {
        if (tenantId == null || tenantId <= 0L) {
            return Set.of("*");
        }

        // 1. Try Redis cache
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        String redisKey = REDIS_KEY_PREFIX + tenantId;
        if (redis != null) {
            try {
                String cachedJson = redis.opsForValue().get(redisKey);
                if (cachedJson != null && !cachedJson.isBlank()) {
                    List<String> list = objectMapper.readValue(cachedJson, new TypeReference<>() {});
                    return Collections.unmodifiableSet(new HashSet<>(list));
                }
            } catch (Exception ex) {
                log.warn("Failed to read tenant features from Redis for tenant {}: {}", tenantId, ex.getMessage());
            }
        }

        // 2. Try Local Cache
        LocalCacheEntry localEntry = localCache.get(tenantId);
        if (localEntry != null && !localEntry.isExpired()) {
            return localEntry.features();
        }

        // 3. Compute from Database
        Set<String> computedFeatures = computeFeaturesFromDatabase(tenantId);

        // 4. Update Redis & Local Cache
        if (redis != null) {
            try {
                String json = objectMapper.writeValueAsString(computedFeatures);
                redis.opsForValue().set(redisKey, json, REDIS_TTL);
            } catch (Exception ex) {
                log.warn("Failed to cache tenant features into Redis for tenant {}: {}", tenantId, ex.getMessage());
            }
        }
        localCache.put(tenantId, new LocalCacheEntry(computedFeatures, System.currentTimeMillis() + LOCAL_TTL_MILLIS));

        return computedFeatures;
    }

    @Override
    public void invalidateCache(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        localCache.remove(tenantId);
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(REDIS_KEY_PREFIX + tenantId);
            } catch (Exception ex) {
                log.warn("Failed to invalidate Redis cache for tenant {}: {}", tenantId, ex.getMessage());
            }
        }
    }

    private Set<String> computeFeaturesFromDatabase(Long tenantId) {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        if (tenantMapper == null) {
            return Set.of("*");
        }

        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || "DISABLED".equalsIgnoreCase(tenant.getStatus()) || Integer.valueOf(1).equals(tenant.getDeleted())) {
            return Set.of();
        }

        Set<String> effective = new HashSet<>();
        Set<String> disabled = new HashSet<>();

        // Platform root tenant (tenantId == 1) defaults to having all system apps enabled unless explicitly disabled
        if (tenantId == 1L) {
            for (String code : List.of("iam", "tenant", "workflow", "file", "crm", "erp", "scheduler", "audit", "integration", "system", "codegen", "dashboard")) {
                effective.add(code);
                effective.add("saas." + code + ".basic");
                effective.add("saas." + code + ".all");
            }
        }

        // Package features
        TenantPackageMapper packageMapper = tenantPackageMapperProvider.getIfAvailable();
        if (packageMapper != null && tenant.getPackageId() != null && tenant.getPackageId() > 0) {
            TenantPackageEntity pkg = packageMapper.selectById(tenant.getPackageId());
            if (pkg != null && pkg.getFeatureFlagsJson() != null) {
                effective.addAll(parseJsonList(pkg.getFeatureFlagsJson()));
            }
        }

        // Tenant custom extra features
        if (tenant.getExtraFeaturesJson() != null) {
            effective.addAll(parseJsonList(tenant.getExtraFeaturesJson()));
        }

        // Tenant custom disabled features
        if (tenant.getDisabledFeaturesJson() != null) {
            disabled.addAll(parseJsonList(tenant.getDisabledFeaturesJson()));
        }

        // plat_tenant_app subscriptions
        TenantAppMapper appMapper = tenantAppMapperProvider.getIfAvailable();
        if (appMapper != null) {
            List<TenantAppEntity> apps = appMapper.selectList(new LambdaQueryWrapper<TenantAppEntity>()
                    .eq(TenantAppEntity::getTenantId, tenantId)
                    .eq(TenantAppEntity::getDeleted, 0));
            LocalDateTime now = LocalDateTime.now();
            for (TenantAppEntity app : apps) {
                String code = app.getAppCode().trim().toLowerCase(Locale.ROOT);
                if ("ENABLED".equalsIgnoreCase(app.getStatus())) {
                    if (app.getExpiredAt() == null || app.getExpiredAt().isAfter(now)) {
                        effective.add(code);
                        effective.add("saas." + code + ".basic");
                        effective.add("saas." + code + ".all");
                    } else {
                        disabled.add(code);
                        disabled.add("saas." + code + ".basic");
                        disabled.add("saas." + code + ".all");
                    }
                } else if ("DISABLED".equalsIgnoreCase(app.getStatus())) {
                    disabled.add(code);
                    disabled.add("saas." + code + ".basic");
                    disabled.add("saas." + code + ".all");
                }
            }
        }

        // If wildcard "*" was present but specific apps were disabled, expand "*" into all known system apps so the rest remain enabled
        if (effective.contains("*") && !disabled.isEmpty()) {
            effective.remove("*");
            for (String code : List.of("iam", "tenant", "workflow", "file", "crm", "erp", "scheduler", "audit", "integration", "system", "codegen", "dashboard")) {
                effective.add(code);
                effective.add("saas." + code + ".basic");
                effective.add("saas." + code + ".all");
            }
        }

        effective.removeAll(disabled);
        return Collections.unmodifiableSet(effective);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            // fallback: comma separated
            return List.of(json.replace("[", "").replace("]", "").replace("\"", "").split(","));
        }
    }
}
