package com.saasbasics.platform.modules.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.integration.dto.OpenAppResponse;
import com.saasbasics.platform.modules.integration.dto.OpenAppSaveRequest;
import com.saasbasics.platform.modules.integration.entity.PlatOpenAppEntity;
import com.saasbasics.platform.modules.integration.mapper.PlatOpenAppMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatOpenAppService {

    private final PlatOpenAppMapper openAppMapper;

    public PlatOpenAppService(PlatOpenAppMapper openAppMapper) {
        this.openAppMapper = openAppMapper;
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
        if (req.id() != null && req.id() > 0) {
            entity = openAppMapper.selectById(req.id());
            if (entity == null || !entity.getTenantId().equals(tenantId) || entity.getDeleted() != 0) {
                throw new BizException("NOT_FOUND", "开放平台应用不存在");
            }
        } else {
            entity = new PlatOpenAppEntity();
            entity.setTenantId(tenantId);
            entity.setAppKey("ak_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setAppSecret("sk_" + UUID.randomUUID().toString().replace("-", ""));
            entity.setCreatedBy(userId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setDeleted(0);
            entity.setVersion(0);
        }

        entity.setAppName(req.appName());
        entity.setStatus(req.status() != null ? req.status() : "ENABLED");
        entity.setRateLimit(req.rateLimit() != null ? req.rateLimit() : 100);
        entity.setAllowedPermissions(req.allowedPermissions() != null ? req.allowedPermissions() : "open:user:read,open:dept:read");
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
                maskSecret(entity.getAppSecret()),
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
        entity.setAppSecret(newSecret);
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
        if (appKey == null || timestampStr == null || nonce == null || signature == null) {
            throw new BizException("OPEN_API_AUTH_FAILED", "缺少必要的 Open API 签名请求头参数 (X-App-Key, X-Timestamp, X-Nonce, X-Signature)");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new BizException("OPEN_API_AUTH_FAILED", "时间戳格式错误");
        }

        long current = System.currentTimeMillis();
        if (Math.abs(current - timestamp) > 300_000) {
            throw new BizException("OPEN_API_TIMESTAMP_EXPIRED", "请求时间戳已失效，偏差不可超过5分钟");
        }

        PlatOpenAppEntity app = openAppMapper.selectOne(new LambdaQueryWrapper<PlatOpenAppEntity>()
                .eq(PlatOpenAppEntity::getAppKey, appKey)
                .eq(PlatOpenAppEntity::getDeleted, 0)
                .last("LIMIT 1"));

        if (app == null || !"ENABLED".equalsIgnoreCase(app.getStatus())) {
            throw new BizException("OPEN_API_APP_INVALID", "AppKey不存在或应用处于停用状态");
        }

        String expectedSignature = calculateHmacSha256(appKey + timestampStr + nonce, app.getAppSecret());
        if (!expectedSignature.equalsIgnoreCase(signature)) {
            throw new BizException("OPEN_API_SIGNATURE_MISMATCH", "签名校验失败，请检查 AppSecret 签名计算算法");
        }

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

    private String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) return "sk_********";
        return secret.substring(0, 6) + "********" + secret.substring(secret.length() - 4);
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
