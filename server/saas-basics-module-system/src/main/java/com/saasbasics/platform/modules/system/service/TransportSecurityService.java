package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.RetainedKeySummary;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportKeyActivateRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportKeyConfigureRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportKeySummary;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportPolicyUpdateRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportRoundtripRequest;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportRoundtripResponse;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportSecurityOverviewResponse;
import com.saasbasics.platform.modules.system.dto.TransportSecurityDtos.TransportTelemetrySummary;
import com.saasbasics.platform.modules.system.entity.TransportCryptoPolicyEntity;
import com.saasbasics.platform.modules.system.mapper.TransportCryptoPolicyMapper;
import com.saasbasics.platform.security.transport.TransportCryptoFilter;
import com.saasbasics.platform.security.transport.TransportCryptoKeyRing;
import com.saasbasics.platform.security.transport.TransportCryptoMode;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyRepository;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyService;
import com.saasbasics.platform.security.transport.TransportCryptoPolicySnapshot;
import com.saasbasics.platform.security.transport.TransportScopeMode;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 传输加密动态控制台业务服务：负责数据库持久化、热策略切换、X25519 密钥配置/轮换、接口目录统计及合规审计。
 */
@Service
public class TransportSecurityService implements TransportCryptoPolicyRepository {

    private static final Logger log = LoggerFactory.getLogger(TransportSecurityService.class);
    private static final String GLOBAL_CODE = "GLOBAL";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectProvider<TransportCryptoPolicyMapper> policyMapperProvider;
    private final TransportCryptoPolicyService policyService;
    private final TransportCryptoKeyRing keyRing;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CredentialVaultService> credentialVaultProvider;
    private final ObjectProvider<PlatformApiCatalogService> apiCatalogProvider;

    public TransportSecurityService(ObjectProvider<TransportCryptoPolicyMapper> policyMapperProvider,
                                    TransportCryptoPolicyService policyService,
                                    TransportCryptoKeyRing keyRing,
                                    AuditTrailService auditTrailService,
                                    ObjectMapper objectMapper,
                                    ObjectProvider<CredentialVaultService> credentialVaultProvider,
                                    ObjectProvider<PlatformApiCatalogService> apiCatalogProvider) {
        this.policyMapperProvider = policyMapperProvider;
        this.policyService = policyService;
        this.keyRing = keyRing;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
        this.credentialVaultProvider = credentialVaultProvider;
        this.apiCatalogProvider = apiCatalogProvider;
    }

    @Override
    public TransportCryptoPolicySnapshot loadGlobalPolicy() {
        TransportCryptoPolicyMapper mapper = policyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        TransportCryptoPolicyEntity entity = findGlobalEntity(mapper);
        if (entity == null) {
            return null;
        }
        Instant updated = entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC)
                : Instant.now();
        int rotationHours = entity.getKeyRotationHours() != null && entity.getKeyRotationHours() > 0
                ? entity.getKeyRotationHours()
                : 24;
        int graceKeys = entity.getMaxGraceKeys() != null && entity.getMaxGraceKeys() > 0
                ? entity.getMaxGraceKeys()
                : 3;

        return new TransportCryptoPolicySnapshot(
                parseMode(entity.getMode()),
                parseScope(entity.getScopeMode()),
                entity.getAlgorithm() != null ? entity.getAlgorithm() : TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                Integer.valueOf(1).equals(entity.getEncryptRequest()),
                Integer.valueOf(1).equals(entity.getEncryptResponse()),
                Integer.valueOf(1).equals(entity.getEncryptQuery()),
                Integer.valueOf(1).equals(entity.getReplayProtection()),
                entity.getTimestampSkewSeconds() != null ? entity.getTimestampSkewSeconds() : 300,
                rotationHours,
                graceKeys,
                readJsonList(entity.getIncludePathsJson()),
                readJsonList(entity.getExcludePathsJson()),
                readJsonList(entity.getSensitivePathsJson()),
                entity.getPolicyVersion() != null ? entity.getPolicyVersion() : 1L,
                updated,
                entity.getRemark()
        );
    }

    @Override
    public TransportCryptoPolicySnapshot saveGlobalPolicy(TransportCryptoPolicySnapshot snapshot) {
        TransportCryptoPolicyMapper mapper = policyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return snapshot;
        }
        TransportCryptoPolicyEntity existing = findGlobalEntity(mapper);
        boolean isNew = existing == null;
        TransportCryptoPolicyEntity entity = isNew ? new TransportCryptoPolicyEntity() : existing;
        entity.setPolicyCode(GLOBAL_CODE);
        entity.setMode(snapshot.mode().name());
        entity.setScopeMode(snapshot.scopeMode().name());
        entity.setAlgorithm(snapshot.algorithm());
        entity.setEncryptRequest(snapshot.encryptRequest() ? 1 : 0);
        entity.setEncryptResponse(snapshot.encryptResponse() ? 1 : 0);
        entity.setEncryptQuery(snapshot.encryptQuery() ? 1 : 0);
        entity.setReplayProtection(snapshot.replayProtection() ? 1 : 0);
        entity.setTimestampSkewSeconds(snapshot.timestampSkewSeconds());
        entity.setKeyRotationHours(snapshot.keyRotationHours());
        entity.setMaxGraceKeys(snapshot.maxGraceKeys());
        entity.setIncludePathsJson(writeJsonList(snapshot.includePaths()));
        entity.setExcludePathsJson(writeJsonList(snapshot.excludePaths()));
        entity.setSensitivePathsJson(writeJsonList(snapshot.sensitivePaths()));
        entity.setPolicyVersion(snapshot.policyVersion());
        entity.setRemark(snapshot.remark());
        if (isNew) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return snapshot;
    }

    public TransportSecurityOverviewResponse getOverview() {
        return toOverview(policyService.getCurrentPolicy());
    }

    public TransportSecurityOverviewResponse updatePolicy(TransportPolicyUpdateRequest request) {
        TransportCryptoPolicySnapshot current = policyService.getCurrentPolicy();
        int rotationHours = request.keyRotationHours() != null ? request.keyRotationHours() : current.keyRotationHours();
        int graceKeys = request.maxGraceKeys() != null ? request.maxGraceKeys() : current.maxGraceKeys();

        TransportCryptoPolicySnapshot target = new TransportCryptoPolicySnapshot(
                parseMode(request.mode()),
                parseScope(request.scopeMode()),
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                request.encryptRequest(),
                request.encryptResponse(),
                request.encryptQuery(),
                request.replayProtection(),
                request.timestampSkewSeconds(),
                rotationHours,
                graceKeys,
                request.includePaths(),
                request.excludePaths(),
                request.sensitivePaths(),
                current.policyVersion() + 1L,
                Instant.now(),
                request.remark()
        );
        TransportCryptoPolicySnapshot saved = policyService.updatePolicy(target);
        keyRing.enforceGraceLimit(saved.maxGraceKeys());
        auditTrailService.record(
                "system",
                "transport_crypto_policy",
                GLOBAL_CODE,
                "UPDATE",
                current.mode().name() + "/" + current.scopeMode().name(),
                saved.mode().name() + "/" + saved.scopeMode().name(),
                true
        );
        return toOverview(saved);
    }

    public TransportSecurityOverviewResponse rotateKey() {
        TransportCryptoPolicySnapshot current = policyService.getCurrentPolicy();
        String previousKid = keyRing.getActiveKey().keyId();
        TransportCryptoKeyRing.ServerKeyEntry nextKey = keyRing.rotateKeyPair(
                null,
                current.maxGraceKeys(),
                "管理员手动轮换 X25519 密钥对"
        );
        TransportCryptoPolicySnapshot bumped = bumpPolicyVersion(
                current,
                current.keyRotationHours(),
                current.maxGraceKeys(),
                "管理员手动轮换 X25519 传输密钥对 (" + nextKey.keyId() + ")"
        );
        auditTrailService.record(
                "system",
                "transport_crypto_key",
                nextKey.keyId(),
                "ROTATE_KEY",
                previousKid,
                nextKey.keyId(),
                true
        );
        return toOverview(bumped);
    }

    public TransportSecurityOverviewResponse configureKey(TransportKeyConfigureRequest request) {
        TransportCryptoPolicySnapshot current = policyService.getCurrentPolicy();
        int rotationHours = request.keyRotationHours() != null ? request.keyRotationHours() : current.keyRotationHours();
        int graceKeys = request.maxGraceKeys() != null ? request.maxGraceKeys() : current.maxGraceKeys();
        String previousKid = keyRing.getActiveKey().keyId();

        String normalizedMode = request.mode().trim().toUpperCase(Locale.ROOT);
        TransportCryptoKeyRing.ServerKeyEntry updatedKey;
        String actionName;
        String remark;
        if ("CUSTOM".equals(normalizedMode)) {
            updatedKey = keyRing.configureCustomKeyPair(
                    request.keyId(),
                    request.privateKeyInput(),
                    graceKeys,
                    request.remark()
            );
            actionName = "IMPORT_CUSTOM_KEY";
            remark = "管理员配置自定义 X25519 传输密钥 (" + updatedKey.keyId() + ")";
        } else {
            updatedKey = keyRing.rotateKeyPair(
                    request.keyId(),
                    graceKeys,
                    (request.remark() != null && !request.remark().isBlank()) ? request.remark() : "管理员按配置轮换生成 X25519 密钥"
            );
            actionName = "CONFIGURE_GENERATE_KEY";
            remark = "管理员生成并应用新 X25519 密钥 (" + updatedKey.keyId() + ")";
        }

        keyRing.enforceGraceLimit(graceKeys);
        TransportCryptoPolicySnapshot bumped = bumpPolicyVersion(current, rotationHours, graceKeys, remark);
        auditTrailService.record(
                "system",
                "transport_crypto_key",
                updatedKey.keyId(),
                actionName,
                previousKid,
                updatedKey.keyId(),
                true
        );
        return toOverview(bumped);
    }

    public TransportSecurityOverviewResponse activateKey(TransportKeyActivateRequest request) {
        TransportCryptoPolicySnapshot current = policyService.getCurrentPolicy();
        String previousKid = keyRing.getActiveKey().keyId();
        TransportCryptoKeyRing.ServerKeyEntry activated = keyRing.activateRetainedKey(request.keyId(), current.maxGraceKeys());
        TransportCryptoPolicySnapshot bumped = bumpPolicyVersion(
                current,
                current.keyRotationHours(),
                current.maxGraceKeys(),
                "切换激活历史保留密钥 (" + activated.keyId() + ")"
        );
        auditTrailService.record(
                "system",
                "transport_crypto_key",
                activated.keyId(),
                "ACTIVATE_KEY",
                previousKid,
                activated.keyId(),
                true
        );
        return toOverview(bumped);
    }

    public PlatformApiCatalogService.ApiCatalogSnapshot getApiCatalog() {
        PlatformApiCatalogService catalog = apiCatalogProvider.getIfAvailable();
        if (catalog == null) {
            return new PlatformApiCatalogService.ApiCatalogSnapshot(
                    new PlatformApiCatalogService.ApiCatalogSummary("saas-basics-server", "主应用服务", 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        return catalog.buildSnapshot(policyService::evaluateDecisionForPattern);
    }

    public TransportRoundtripResponse verifyRoundtrip(TransportRoundtripRequest request, HttpServletRequest httpRequest) {
        TransportCryptoPolicySnapshot policy = policyService.getCurrentPolicy();
        TransportCryptoKeyRing.ServerKeyEntry key = keyRing.getActiveKey();
        boolean encrypted = "1".equals(httpRequest.getHeader(TransportCryptoFilter.HEADER_ENCRYPTED))
                || "true".equalsIgnoreCase(httpRequest.getHeader(TransportCryptoFilter.HEADER_ENCRYPTED));
        String echo = request != null && request.probeText() != null ? request.probeText() : "OK";
        return new TransportRoundtripResponse(
                echo,
                encrypted,
                policy.mode().name(),
                policy.scopeMode().name(),
                key.keyId(),
                policy.policyVersion(),
                System.currentTimeMillis()
        );
    }

    private TransportCryptoPolicySnapshot bumpPolicyVersion(
            TransportCryptoPolicySnapshot current,
            int rotationHours,
            int graceKeys,
            String remark) {
        return policyService.updatePolicy(new TransportCryptoPolicySnapshot(
                current.mode(),
                current.scopeMode(),
                current.algorithm(),
                current.encryptRequest(),
                current.encryptResponse(),
                current.encryptQuery(),
                current.replayProtection(),
                current.timestampSkewSeconds(),
                rotationHours,
                graceKeys,
                current.includePaths(),
                current.excludePaths(),
                current.sensitivePaths(),
                current.policyVersion() + 1L,
                Instant.now(),
                remark
        ));
    }

    private TransportSecurityOverviewResponse toOverview(TransportCryptoPolicySnapshot snapshot) {
        TransportCryptoKeyRing.ServerKeyEntry key = keyRing.getActiveKey();
        TransportCryptoPolicyService.TelemetryStats stats = policyService.getTelemetryStats();
        CredentialVaultService vault = credentialVaultProvider != null ? credentialVaultProvider.getIfAvailable() : null;
        List<RetainedKeySummary> retained = keyRing.listRetainedKeys().stream()
                .map(k -> new RetainedKeySummary(
                        k.keyId(),
                        k.keySource(),
                        k.publicKeyBase64(),
                        k.fingerprintSha256(),
                        k.issuedAtEpochMs(),
                        k.active(),
                        k.remark()
                ))
                .toList();

        return new TransportSecurityOverviewResponse(
                snapshot.mode().name(),
                snapshot.scopeMode().name(),
                snapshot.algorithm(),
                snapshot.encryptRequest(),
                snapshot.encryptResponse(),
                snapshot.encryptQuery(),
                snapshot.replayProtection(),
                snapshot.timestampSkewSeconds(),
                snapshot.keyRotationHours(),
                snapshot.maxGraceKeys(),
                snapshot.includePaths(),
                snapshot.excludePaths(),
                snapshot.sensitivePaths(),
                snapshot.policyVersion(),
                snapshot.updatedAt() != null ? snapshot.updatedAt().toEpochMilli() : System.currentTimeMillis(),
                snapshot.remark(),
                new TransportKeySummary(
                        key.keyId(),
                        key.keySource(),
                        key.publicKeyBase64(),
                        key.fingerprintSha256(),
                        key.issuedAt().toEpochMilli(),
                        keyRing.loadedKeyCount(),
                        snapshot.keyRotationHours(),
                        snapshot.maxGraceKeys(),
                        vault == null || vault.isUsingDefaultMasterKey(),
                        vault != null ? vault.activeKeyVersion() : "v1",
                        retained
                ),
                new TransportTelemetrySummary(
                        stats.totalRequests(),
                        stats.encryptedRequests(),
                        stats.plaintextAllowed(),
                        stats.rejectedPlaintext(),
                        stats.rejectedReplay(),
                        stats.decryptErrors()
                )
        );
    }

    private TransportCryptoPolicyEntity findGlobalEntity(TransportCryptoPolicyMapper mapper) {
        return mapper.selectOne(new LambdaQueryWrapper<TransportCryptoPolicyEntity>()
                .eq(TransportCryptoPolicyEntity::getPolicyCode, GLOBAL_CODE)
                .eq(TransportCryptoPolicyEntity::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private TransportCryptoMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransportCryptoMode.OPTIONAL;
        }
        try {
            return TransportCryptoMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TransportCryptoMode.OPTIONAL;
        }
    }

    private TransportScopeMode parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransportScopeMode.ALL_API;
        }
        try {
            return TransportScopeMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TransportScopeMode.ALL_API;
        }
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 解析路径 JSON 失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeJsonList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : Collections.emptyList());
        } catch (Exception ex) {
            return "[]";
        }
    }
}
