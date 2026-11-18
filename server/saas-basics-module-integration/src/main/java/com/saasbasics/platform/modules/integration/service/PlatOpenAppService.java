package com.saasbasics.platform.modules.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.integration.dto.OpenAppResponse;
import com.saasbasics.platform.modules.integration.dto.OpenAppSaveRequest;
import com.saasbasics.platform.modules.integration.entity.PlatOpenAppEntity;
import com.saasbasics.platform.modules.integration.mapper.PlatOpenAppMapper;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatOpenAppService {

    private static final long NONCE_WINDOW_MS = 300_000L;

    private final PlatOpenAppMapper openAppMapper;
    private final ObjectProvider<CredentialVaultService> credentialVaultProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ConcurrentHashMap<String, Long> localNonceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, long[]> localRateCache = new ConcurrentHashMap<>();

    public PlatOpenAppService(PlatOpenAppMapper openAppMapper,
                              ObjectProvider<CredentialVaultService> credentialVaultProvider) {
        this(openAppMapper, credentialVaultProvider, null);
    }

    @Autowired
    public PlatOpenAppService(PlatOpenAppMapper openAppMapper,
                              ObjectProvider<CredentialVaultService> credentialVaultProvider,
                              ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.openAppMapper = openAppMapper;
        this.credentialVaultProvider = credentialVaultProvider;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public List<OpenAppResponse> listApps() {
        Long tenantId = getTenantId();
        List<PlatOpenAppEntity> list = openAppMapper.selectList(new LambdaQueryWrapper<PlatOpenAppEntity>()
                .eq(PlatOpenAppEntity::getTenantId, tenantId)
                .eq(PlatOpenAppEntity::getDeleted, 0)
                .orderByDesc(PlatOpenAppEntity::getId));

        return list.stream().map(a -> new OpenAppResponse(
                a.getId(),
                a.getTenantId(),
                a.getAppKey(),
                maskSecret(a.getAppSecret()),
                a.getAppName(),
                a.getStatus(),
                a.getRateLimit(),
                a.getAllowedPermissions(),
                a.getRemark(),
                a.getCreatedAt()
        )).toList();
    }

    @Transactional
    public OpenAppResponse saveApp(OpenAppSaveRequest req) {
        Long tenantId = getTenantId();
        Long userId = getUserId();

        PlatOpenAppEntity entity;
        String createdRawSecret = null;
        if (req.id() != null && req.id() > 0) {
            entity = openAppMapper.selectById(req.id());
            if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
                throw new BizException("NOT_FOUND", "开放平台应用不存在");
            }
        } else {
            entity = new PlatOpenAppEntity();
            entity.setTenantId(tenantId);
            entity.setAppKey("ak_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            createdRawSecret = "sk_" + UUID.randomUUID().toString().replace("-", "");
            entity.setAppSecret(encryptSecret(createdRawSecret));
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            entity.setVersion(0);
        }

        entity.setAppName(req.appName());
        entity.setStatus(req.status() != null ? req.status() : "ENABLED");
        entity.setRateLimit(req.rateLimit() != null ? req.rateLimit() : 100);
        entity.setAllowedPermissions(req.allowedPermissions() != null
                ? req.allowedPermissions()
                : "open:user:read,open:crm:read,open:erp:read");
        entity.setRemark(req.remark());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());

        if (entity.getId() != null) {
            openAppMapper.updateById(entity);
        } else {
            openAppMapper.insert(entity);
        }

        return new OpenAppResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getAppKey(),
                createdRawSecret != null ? createdRawSecret : maskSecret(entity.getAppSecret()),
                entity.getAppName(),
                entity.getStatus(),
                entity.getRateLimit(),
                entity.getAllowedPermissions(),
                entity.getRemark(),
                entity.getCreatedAt()
        );
    }

    @Transactional
    public OpenAppResponse regenerateSecret(Long id) {
        Long tenantId = getTenantId();
        PlatOpenAppEntity entity = openAppMapper.selectById(id);
        if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
            throw new BizException("NOT_FOUND", "开放平台应用不存在");
        }

        String newSecret = "sk_" + UUID.randomUUID().toString().replace("-", "");
        entity.setAppSecret(encryptSecret(newSecret));
        entity.setUpdatedAt(LocalDateTime.now());
        openAppMapper.updateById(entity);

        return new OpenAppResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getAppKey(),
                newSecret, // 仅重置时暴露一次明文
                entity.getAppName(),
                entity.getStatus(),
                entity.getRateLimit(),
                entity.getAllowedPermissions(),
                entity.getRemark(),
                entity.getCreatedAt()
        );
    }

    @Transactional
    public void deleteApp(Long id) {
        Long tenantId = getTenantId();
        PlatOpenAppEntity entity = openAppMapper.selectById(id);
        if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
            throw new BizException("NOT_FOUND", "开放平台应用不存在");
        }
        entity.setDeleted(1);
        entity.setDeletedAt(LocalDateTime.now());
        openAppMapper.updateById(entity);
    }

    public com.saasbasics.platform.modules.integration.dto.OpenAppAuthContext authenticateOpenApi(String appKey, String timestampStr, String nonce, String signature) {
        if (appKey == null || appKey.isBlank()
                || timestampStr == null || timestampStr.isBlank()
                || nonce == null || nonce.isBlank()
                || signature == null || signature.isBlank()) {
            throw new BizException("OPEN_API_AUTH_FAILED", "缺少必要的 Open API 签名请求头参数 (X-App-Key, X-Timestamp, X-Nonce, X-Signature)");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            throw new BizException("OPEN_API_AUTH_FAILED", "时间戳格式错误");
        }

        long current = System.currentTimeMillis();
        if (Math.abs(current - timestamp) > NONCE_WINDOW_MS) {
            throw new BizException("OPEN_API_TIMESTAMP_EXPIRED", "请求时间戳已失效，偏差不可超过5分钟");
        }

        PlatOpenAppEntity app;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.AUTHENTICATION_TOKEN_RESOLUTION,
                "Resolve Open API AppKey")) {
            app = openAppMapper.selectOne(new LambdaQueryWrapper<PlatOpenAppEntity>()
                    .eq(PlatOpenAppEntity::getAppKey, appKey.trim())
                    .eq(PlatOpenAppEntity::getDeleted, 0)
                    .last("LIMIT 1"));
        }

        if (app == null || !"ENABLED".equalsIgnoreCase(app.getStatus())) {
            throw new BizException("OPEN_API_APP_INVALID", "AppKey不存在或应用处于停用状态");
        }

        String plainSecret = decryptSecret(app.getAppSecret());
        String expectedSignature = calculateHmacSha256(appKey.trim() + timestampStr.trim() + nonce.trim(), plainSecret);
        if (!MessageDigest.isEqual(
                expectedSignature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new BizException("OPEN_API_SIGNATURE_MISMATCH", "签名校验失败，请检查 AppSecret 签名计算算法");
        }

        enforceNonceReplayGuard(app.getAppKey(), nonce.trim(), current);
        enforceRateLimit(app.getAppKey(), app.getRateLimit() != null ? app.getRateLimit() : 100);

        return new com.saasbasics.platform.modules.integration.dto.OpenAppAuthContext(
                app.getId(),
                app.getTenantId(),
                app.getAppKey(),
                app.getAppName(),
                app.getStatus(),
                app.getRateLimit(),
                app.getAllowedPermissions()
        );
    }

    public void requireScope(com.saasbasics.platform.modules.integration.dto.OpenAppAuthContext ctx, String requiredScope) {
        if (ctx == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Open API context is missing");
        }
        String raw = ctx.allowedPermissions();
        if (raw == null || raw.isBlank()) {
            throw new BizException("AUTH_FORBIDDEN", "Missing Open API scope: " + requiredScope);
        }
        boolean matched = Arrays.stream(raw.split(","))
                .map(String::trim)
                .anyMatch(s -> "*".equals(s) || "*:*:*".equals(s) || s.equalsIgnoreCase(requiredScope));
        if (!matched) {
            throw new BizException("AUTH_FORBIDDEN", "Missing Open API scope: " + requiredScope);
        }
    }

    private void enforceNonceReplayGuard(String appKey, String nonce, long nowMs) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(
                        "open:nonce:" + appKey + ":" + nonce,
                        "1",
                        Duration.ofMillis(NONCE_WINDOW_MS)
                );
                if (!Boolean.TRUE.equals(ok)) {
                    throw new BizException("OPEN_API_NONCE_REPLAYED", "Duplicate X-Nonce detected");
                }
                return;
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ignored) {
                // Fallback to local memory window when Redis is temporarily unavailable
            }
        }
        if (localNonceCache.size() > 10_000) {
            localNonceCache.entrySet().removeIf(e -> e.getValue() < nowMs);
        }
        String key = appKey + ":" + nonce;
        Long existingExpiry = localNonceCache.putIfAbsent(key, nowMs + NONCE_WINDOW_MS);
        if (existingExpiry != null) {
            if (existingExpiry > nowMs) {
                throw new BizException("OPEN_API_NONCE_REPLAYED", "Duplicate X-Nonce detected");
            }
            localNonceCache.put(key, nowMs + NONCE_WINDOW_MS);
        }
    }

    private void enforceRateLimit(String appKey, int maxPerSecond) {
        int limit = Math.max(1, maxPerSecond);
        long currentSecond = System.currentTimeMillis() / 1000L;
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                String rKey = "open:rl:" + appKey + ":" + currentSecond;
                Long count = redis.opsForValue().increment(rKey);
                if (count != null && count == 1L) {
                    redis.expire(rKey, Duration.ofSeconds(2));
                }
                if (count != null && count > limit) {
                    throw new BizException("RATE_LIMIT_EXCEEDED", "Open API rate limit exceeded (" + limit + " QPS)");
                }
                return;
            } catch (BizException ex) {
                throw ex;
            } catch (Exception ignored) {
            }
        }
        long[] bucket = localRateCache.computeIfAbsent(appKey, k -> new long[]{currentSecond, 0L});
        synchronized (bucket) {
            if (bucket[0] != currentSecond) {
                bucket[0] = currentSecond;
                bucket[1] = 0L;
            }
            bucket[1]++;
            if (bucket[1] > limit) {
                throw new BizException("RATE_LIMIT_EXCEEDED", "Open API rate limit exceeded (" + limit + " QPS)");
            }
        }
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new BizException("CRYPTO_ERROR", "HMAC计算异常");
        }
    }

    private String encryptSecret(String secret) {
        CredentialVaultService vault = credentialVaultProvider.getIfAvailable();
        return vault != null ? vault.encrypt(secret) : secret;
    }

    private String decryptSecret(String storedSecret) {
        CredentialVaultService vault = credentialVaultProvider.getIfAvailable();
        return vault != null ? vault.decrypt(storedSecret) : storedSecret;
    }

    private String maskSecret(String secret) {
        CredentialVaultService vault = credentialVaultProvider.getIfAvailable();
        String plain = vault != null ? vault.decrypt(secret) : secret;
        if (plain == null || plain.length() < 8) return "sk_********";
        return plain.substring(0, 6) + "********" + plain.substring(plain.length() - 4);
    }

    private Long getTenantId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.tenantId() != null ? p.tenantId() : 1L;
    }

    private Long getUserId() {
        AuthPrincipal p = AuthContext.get();
        return p != null && p.userId() != null ? p.userId() : 1L;
    }
}
