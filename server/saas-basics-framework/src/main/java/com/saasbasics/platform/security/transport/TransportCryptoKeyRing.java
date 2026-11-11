package com.saasbasics.platform.security.transport;

import com.saasbasics.platform.security.vault.CredentialVaultService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;
import javax.crypto.KeyAgreement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 传输加密 X25519 密钥环（支持 DB Vault 持久化、自定义密钥导入、平滑热轮换、宽限窗口保留及多节点共享）。
 */
@Component
@EnableConfigurationProperties(TransportCryptoProperties.class)
public class TransportCryptoKeyRing implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TransportCryptoKeyRing.class);
    private static final String REDIS_ACTIVE_KID = "saas:transport:keyring:active_kid";
    private static final String REDIS_KEY_PREFIX = "saas:transport:keyring:entry:";
    private static final byte[] X25519_SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b656e032100");
    private static final byte[] X25519_BASE_POINT_U9 = createBasePointU9();
    private static final Pattern KID_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]{3,64}$");

    public static final String SOURCE_AUTO = "AUTO_GENERATED";
    public static final String SOURCE_CUSTOM = "CUSTOM_IMPORTED";

    private final TransportCryptoProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<CredentialVaultService> vaultServiceProvider;
    private final ObjectProvider<TransportCryptoKeyRepository> keyRepositoryProvider;

    private final ConcurrentHashMap<String, ServerKeyEntry> keyStore = new ConcurrentHashMap<>();
    private final Deque<String> keyOrder = new ConcurrentLinkedDeque<>();
    private volatile String activeKeyId;
    private volatile boolean initializedFromPersistence = false;

    public record ServerKeyEntry(
            String keyId,
            String keySource,
            PrivateKey privateKey,
            byte[] publicKeyRaw32,
            String publicKeyBase64,
            String fingerprintSha256,
            Instant issuedAt,
            String remark
    ) {
    }

    public record RetainedKeyMetadata(
            String keyId,
            String keySource,
            String publicKeyBase64,
            String fingerprintSha256,
            long issuedAtEpochMs,
            boolean active,
            String remark
    ) {
    }

    public TransportCryptoKeyRing(TransportCryptoProperties properties,
                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                  ObjectProvider<CredentialVaultService> vaultServiceProvider) {
        this(properties, redisTemplateProvider, vaultServiceProvider, null);
    }

    @Autowired
    public TransportCryptoKeyRing(TransportCryptoProperties properties,
                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                  ObjectProvider<CredentialVaultService> vaultServiceProvider,
                                  ObjectProvider<TransportCryptoKeyRepository> keyRepositoryProvider) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.vaultServiceProvider = vaultServiceProvider;
        this.keyRepositoryProvider = keyRepositoryProvider;
        initializeBootstrapKey();
    }

    private synchronized void initializeBootstrapKey() {
        if (loadActiveFromRedis()) {
            return;
        }
        generateInternalKeyPair(null, properties.getMaxGraceKeys(), false, "启动阶段临时引导密钥");
    }

    @Override
    public synchronized void afterSingletonsInstantiated() {
        reloadFromPersistence();
    }

    public synchronized void reloadFromPersistence() {
        TransportCryptoKeyRepository repo = keyRepositoryProvider != null ? keyRepositoryProvider.getIfAvailable() : null;
        CredentialVaultService vault = vaultServiceProvider.getIfAvailable();
        if (repo == null || vault == null) {
            return;
        }
        try {
            int limit = Math.max(4, properties.getMaxGraceKeys() + 4);
            List<TransportCryptoKeyRepository.PersistedKeyRecord> persisted = repo.loadRecentKeys(limit);
            if (persisted != null && !persisted.isEmpty()) {
                String restoredActive = null;
                List<ServerKeyEntry> restoredEntries = new ArrayList<>();
                for (TransportCryptoKeyRepository.PersistedKeyRecord rec : persisted) {
                    try {
                        ServerKeyEntry entry = decodePersistedRecord(rec, vault);
                        restoredEntries.add(entry);
                        if (rec.active() && restoredActive == null) {
                            restoredActive = entry.keyId();
                        }
                    } catch (Exception ex) {
                        log.warn("[TransportCrypto] 恢复数据库密钥 {} 失败: {}", rec.keyId(), ex.getMessage());
                    }
                }
                if (!restoredEntries.isEmpty()) {
                    // 按历史从旧到新注册，保证活跃密钥位于队列首位
                    for (int i = restoredEntries.size() - 1; i >= 0; i--) {
                        ServerKeyEntry item = restoredEntries.get(i);
                        if (!item.keyId().equals(restoredActive)) {
                            registerEntry(item, Math.max(properties.getMaxGraceKeys(), restoredEntries.size()), false);
                        }
                    }
                    String targetActive = restoredActive != null ? restoredActive : restoredEntries.get(0).keyId();
                    ServerKeyEntry activeEntry = restoredEntries.stream()
                            .filter(e -> e.keyId().equals(targetActive))
                            .findFirst()
                            .orElse(restoredEntries.get(0));
                    registerEntry(activeEntry, Math.max(properties.getMaxGraceKeys(), restoredEntries.size()), true);
                    persistToRedis(activeEntry);
                    this.initializedFromPersistence = true;
                    log.info("[TransportCrypto] 已从持久化密钥环加载 {} 个密钥，当前活跃 kid={}",
                            restoredEntries.size(), activeEntry.keyId());
                    return;
                }
            }
            // 数据库尚无记录时，将当前引导活跃密钥持久化落库
            ServerKeyEntry active = getActiveKey();
            persistToRepository(active);
            persistToRedis(active);
            this.initializedFromPersistence = true;
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 持久化密钥环同步异常，保持内存/Redis 密钥运行: {}", ex.getMessage());
        }
    }

    public ServerKeyEntry getActiveKey() {
        syncFromRedisIfChanged();
        ServerKeyEntry current = activeKeyId == null ? null : keyStore.get(activeKeyId);
        if (current == null) {
            return rotateKeyPair();
        }
        return current;
    }

    public ServerKeyEntry findKey(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return null;
        }
        ServerKeyEntry local = keyStore.get(keyId);
        if (local != null) {
            return local;
        }
        return loadEntryFromRedis(keyId, false);
    }

    public int loadedKeyCount() {
        return keyStore.size();
    }

    public List<RetainedKeyMetadata> listRetainedKeys() {
        List<RetainedKeyMetadata> list = new ArrayList<>();
        String currentActive = this.activeKeyId;
        for (String kid : keyOrder) {
            ServerKeyEntry entry = keyStore.get(kid);
            if (entry != null) {
                list.add(new RetainedKeyMetadata(
                        entry.keyId(),
                        entry.keySource(),
                        entry.publicKeyBase64(),
                        entry.fingerprintSha256(),
                        entry.issuedAt().toEpochMilli(),
                        entry.keyId().equals(currentActive),
                        entry.remark()
                ));
            }
        }
        return list;
    }

    /**
     * 生成新的随机 X25519 密钥对并设为活跃密钥（使用默认前缀与宽限期）。
     */
    public synchronized ServerKeyEntry rotateKeyPair() {
        return rotateKeyPair(null, properties.getMaxGraceKeys(), "系统自动或手动轮换 X25519 密钥对");
    }

    public synchronized ServerKeyEntry rotateKeyPair(String customKeyId, int maxGraceKeys, String remark) {
        return generateInternalKeyPair(customKeyId, maxGraceKeys, true, remark);
    }

    /**
     * 导入管理员指定的自定义 X25519 私钥（支持 PEM/PKCS#8 Base64 或 32 字节 Hex/Base64 种子）。
     */
    public synchronized ServerKeyEntry configureCustomKeyPair(
            String customKeyId,
            String privateKeyInput,
            int maxGraceKeys,
            String remark) {
        if (privateKeyInput == null || privateKeyInput.isBlank()) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG", "请输入有效的 X25519 私钥内容");
        }
        try {
            PrivateKey privateKey = parseX25519PrivateKey(privateKeyInput);
            byte[] rawPublic32 = derivePublicKeyFromPrivate(privateKey);
            byte[] encodedPkcs8 = privateKey.getEncoded();
            if (encodedPkcs8 == null || encodedPkcs8.length == 0) {
                throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG", "该私钥不支持标准 PKCS#8 序列化");
            }
            // 重新通过 KeyFactory 标准化私钥对象确保 getEncoded() 统一
            PrivateKey normalizedPriv = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(encodedPkcs8));
            String pubB64 = Base64.getEncoder().encodeToString(rawPublic32);
            String fingerprint = sha256Hex(rawPublic32);
            Instant now = Instant.now();
            String kid = resolveCustomKeyId(customKeyId, "k_custom_" + Long.toString(now.toEpochMilli(), 36) + "_" + fingerprint.substring(0, 8));

            ServerKeyEntry entry = new ServerKeyEntry(
                    kid,
                    SOURCE_CUSTOM,
                    normalizedPriv,
                    rawPublic32,
                    pubB64,
                    fingerprint,
                    now,
                    (remark == null || remark.isBlank()) ? "管理员自定义配置 X25519 密钥" : remark.trim()
            );
            registerEntry(entry, maxGraceKeys, true);
            persistToRepository(entry);
            persistToRedis(entry);
            log.info("[TransportCrypto] 已启用自定义 X25519 密钥: kid={}, fp={}", kid, fingerprint.substring(0, 12));
            return entry;
        } catch (TransportCryptoException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG",
                    "无法识别的 X25519 私钥格式（请提供 32 字节 Hex/Base64 种子或 PKCS#8 PEM/Base64）：" + ex.getMessage());
        }
    }

    /**
     * 将宽限期内保留的密钥重新激活为当前主密钥，支持应急平滑回滚。
     */
    public synchronized ServerKeyEntry activateRetainedKey(String keyId, int maxGraceKeys) {
        if (keyId == null || keyId.isBlank()) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG", "密钥 ID 不能为空");
        }
        ServerKeyEntry found = findKey(keyId.trim());
        if (found == null) {
            throw new TransportCryptoException(404, "TRANSPORT_KEY_NOT_FOUND", "未找到指定历史密钥，可能已超出宽限保留期: " + keyId);
        }
        registerEntry(found, maxGraceKeys, true);
        persistToRepository(found);
        persistToRedis(found);
        log.info("[TransportCrypto] 已切换激活历史密钥: kid={}", found.keyId());
        return found;
    }

    public synchronized void enforceGraceLimit(int maxGraceKeys) {
        int maxRetained = Math.max(2, maxGraceKeys + 1);
        String currentActive = this.activeKeyId;
        while (keyOrder.size() > maxRetained) {
            String evicted = keyOrder.pollLast();
            if (evicted != null && !evicted.equals(currentActive)) {
                keyStore.remove(evicted);
            }
        }
    }

    public static PublicKey decodeClientRawPublicKey(byte[] raw32) {
        if (raw32 == null || raw32.length != 32) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY", "客户端临时公钥长度无效");
        }
        try {
            byte[] spki = new byte[X25519_SPKI_PREFIX.length + 32];
            System.arraycopy(X25519_SPKI_PREFIX, 0, spki, 0, X25519_SPKI_PREFIX.length);
            System.arraycopy(raw32, 0, spki, X25519_SPKI_PREFIX.length, 32);
            return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (GeneralSecurityException ex) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY", "无法解析客户端 X25519 公钥");
        }
    }

    private ServerKeyEntry generateInternalKeyPair(String customKeyId, int maxGraceKeys, boolean persist, String remark) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair keyPair = kpg.generateKeyPair();
            byte[] spki = keyPair.getPublic().getEncoded();
            byte[] raw32 = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
            String pubB64 = Base64.getEncoder().encodeToString(raw32);
            String fingerprint = sha256Hex(raw32);
            Instant now = Instant.now();
            String defaultKid = "k_" + Long.toString(now.toEpochMilli(), 36) + "_" + fingerprint.substring(0, 8);
            String kid = resolveCustomKeyId(customKeyId, defaultKid);

            ServerKeyEntry entry = new ServerKeyEntry(
                    kid,
                    SOURCE_AUTO,
                    keyPair.getPrivate(),
                    raw32,
                    pubB64,
                    fingerprint,
                    now,
                    remark != null ? remark : "系统生成 X25519 密钥对"
            );
            registerEntry(entry, maxGraceKeys, true);
            if (persist) {
                persistToRepository(entry);
                persistToRedis(entry);
                log.info("[TransportCrypto] X25519 传输密钥已轮换: kid={}, fp={}", kid, fingerprint.substring(0, 12));
            }
            return entry;
        } catch (TransportCryptoException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("初始化 X25519 传输密钥对失败", ex);
        }
    }

    private PrivateKey parseX25519PrivateKey(String rawInput) throws GeneralSecurityException {
        String cleaned = rawInput.trim();
        if (cleaned.contains("BEGIN PRIVATE KEY")) {
            String pemBody = cleaned
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs8 = Base64.getDecoder().decode(pemBody);
            return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        }

        String compact = cleaned.replaceAll("\\s+", "");
        if (compact.startsWith("0x") || compact.startsWith("0X")) {
            compact = compact.substring(2);
        }

        // 1) Hex 编码（64 字符 = 32B 原始标量；更长则尝试 PKCS#8 DER）
        if (compact.matches("^[0-9a-fA-F]+$") && compact.length() % 2 == 0) {
            byte[] hexBytes = HexFormat.of().parseHex(compact);
            if (hexBytes.length == 32) {
                return buildFromRawScalar32(hexBytes);
            }
            return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(hexBytes));
        }

        // 2) Base64 / Base64URL 编码
        byte[] decoded = decodeBase64Flexible(compact);
        if (decoded.length == 32) {
            return buildFromRawScalar32(decoded);
        }
        return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PrivateKey buildFromRawScalar32(byte[] scalar32) throws GeneralSecurityException {
        boolean allZero = true;
        for (byte b : scalar32) {
            if (b != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG", "X25519 私钥不能为全零");
        }
        return KeyFactory.getInstance("X25519")
                .generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, scalar32));
    }

    private byte[] derivePublicKeyFromPrivate(PrivateKey privateKey) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(privateKey);
        ka.doPhase(decodeClientRawPublicKey(X25519_BASE_POINT_U9), true);
        byte[] derived = ka.generateSecret();
        if (derived == null || derived.length != 32) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG", "无法从该私钥推导有效的 X25519 公钥");
        }
        return derived;
    }

    private byte[] decodeBase64Flexible(String input) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException ignored) {
            String padded = input.replace('-', '+').replace('_', '/');
            int rem = padded.length() % 4;
            if (rem > 0) {
                padded += "=".repeat(4 - rem);
            }
            return Base64.getDecoder().decode(padded);
        }
    }

    private String resolveCustomKeyId(String customKeyId, String fallback) {
        if (customKeyId == null || customKeyId.isBlank()) {
            return fallback;
        }
        String trimmed = customKeyId.trim();
        if (!KID_PATTERN.matcher(trimmed).matches()) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY_CONFIG",
                    "密钥 ID 须为 3-64 位字母、数字或 . _ : - 组合");
        }
        return trimmed;
    }

    private ServerKeyEntry decodePersistedRecord(
            TransportCryptoKeyRepository.PersistedKeyRecord rec,
            CredentialVaultService vault) throws GeneralSecurityException {
        String plainPrivB64 = vault.decrypt(rec.sealedPrivateKey());
        byte[] pkcs8 = Base64.getDecoder().decode(plainPrivB64);
        PrivateKey priv = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        byte[] raw32 = Base64.getDecoder().decode(rec.publicKeyBase64());
        return new ServerKeyEntry(
                rec.keyId(),
                rec.keySource() != null ? rec.keySource() : SOURCE_AUTO,
                priv,
                raw32,
                rec.publicKeyBase64(),
                rec.fingerprintSha256(),
                rec.issuedAt() != null ? rec.issuedAt() : Instant.now(),
                rec.remark()
        );
    }

    private void registerEntry(ServerKeyEntry entry, int maxGraceKeys, boolean makeActive) {
        keyStore.put(entry.keyId(), entry);
        keyOrder.remove(entry.keyId());
        if (makeActive) {
            keyOrder.addFirst(entry.keyId());
            this.activeKeyId = entry.keyId();
        } else {
            keyOrder.addLast(entry.keyId());
        }

        int effectiveGrace = maxGraceKeys > 0 ? maxGraceKeys : properties.getMaxGraceKeys();
        int maxRetained = Math.max(2, effectiveGrace + 1);
        String currentActive = this.activeKeyId;
        while (keyOrder.size() > maxRetained) {
            String evicted = keyOrder.pollLast();
            if (evicted != null && !evicted.equals(currentActive)) {
                keyStore.remove(evicted);
            }
        }
    }

    private void persistToRepository(ServerKeyEntry entry) {
        TransportCryptoKeyRepository repo = keyRepositoryProvider != null ? keyRepositoryProvider.getIfAvailable() : null;
        CredentialVaultService vault = vaultServiceProvider.getIfAvailable();
        if (repo == null || vault == null) {
            return;
        }
        try {
            String privB64 = Base64.getEncoder().encodeToString(entry.privateKey().getEncoded());
            String sealedPriv = vault.encrypt(privB64);
            repo.saveActiveKey(new TransportCryptoKeyRepository.PersistedKeyRecord(
                    entry.keyId(),
                    entry.keySource(),
                    sealedPriv,
                    entry.publicKeyBase64(),
                    entry.fingerprintSha256(),
                    true,
                    entry.issuedAt(),
                    entry.remark()
            ));
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 写入数据库密钥环失败，继续由内存与 Redis 提供服务: {}", ex.getMessage());
        }
    }

    private boolean loadActiveFromRedis() {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            String kid = redis.opsForValue().get(REDIS_ACTIVE_KID);
            if (kid == null || kid.isBlank()) {
                return false;
            }
            ServerKeyEntry loaded = loadEntryFromRedis(kid, true);
            return loaded != null;
        } catch (Exception ex) {
            log.debug("[TransportCrypto] Redis 密钥环读取降级至本地密钥: {}", ex.getMessage());
            return false;
        }
    }

    private void syncFromRedisIfChanged() {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String remoteKid = redis.opsForValue().get(REDIS_ACTIVE_KID);
            if (remoteKid != null && !remoteKid.isBlank() && !remoteKid.equals(this.activeKeyId)) {
                if (loadEntryFromRedis(remoteKid, true) == null && !initializedFromPersistence) {
                    reloadFromPersistence();
                }
            }
        } catch (Exception ignored) {
            // 降级容错
        }
    }

    private ServerKeyEntry loadEntryFromRedis(String kid, boolean makeActive) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        CredentialVaultService vault = vaultServiceProvider.getIfAvailable();
        if (redis == null || vault == null) {
            return null;
        }
        try {
            String serialized = redis.opsForValue().get(REDIS_KEY_PREFIX + kid);
            if (serialized == null || serialized.isBlank()) {
                return null;
            }
            String decrypted = vault.decrypt(serialized);
            String[] parts = decrypted.split("\\|", 5);
            if (parts.length < 3) {
                return null;
            }
            byte[] pkcs8 = Base64.getDecoder().decode(parts[0]);
            byte[] raw32 = Base64.getDecoder().decode(parts[1]);
            Instant issuedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
            String keySource = parts.length >= 4 && !parts[3].isBlank() ? parts[3] : SOURCE_AUTO;
            String remark = parts.length >= 5 ? new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8) : null;
            PrivateKey priv = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            ServerKeyEntry entry = new ServerKeyEntry(
                    kid,
                    keySource,
                    priv,
                    raw32,
                    Base64.getEncoder().encodeToString(raw32),
                    sha256Hex(raw32),
                    issuedAt,
                    remark
            );
            registerEntry(entry, properties.getMaxGraceKeys(), makeActive);
            return entry;
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 从 Redis 恢复密钥 {} 失败: {}", kid, ex.getMessage());
            return null;
        }
    }

    private void persistToRedis(ServerKeyEntry entry) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        CredentialVaultService vault = vaultServiceProvider.getIfAvailable();
        if (redis == null || vault == null) {
            return;
        }
        try {
            String remarkB64 = entry.remark() != null
                    ? Base64.getEncoder().encodeToString(entry.remark().getBytes(StandardCharsets.UTF_8))
                    : "";
            String rawPayload = Base64.getEncoder().encodeToString(entry.privateKey().getEncoded())
                    + "|" + entry.publicKeyBase64()
                    + "|" + entry.issuedAt().toEpochMilli()
                    + "|" + (entry.keySource() != null ? entry.keySource() : SOURCE_AUTO)
                    + "|" + remarkB64;
            String sealed = vault.encrypt(rawPayload);
            long ttlHours = Math.max(24L, properties.getKeyRotationHours() * 2L);
            redis.opsForValue().set(REDIS_KEY_PREFIX + entry.keyId(), sealed, java.time.Duration.ofHours(ttlHours));
            redis.opsForValue().set(REDIS_ACTIVE_KID, entry.keyId(), java.time.Duration.ofHours(ttlHours));
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 写入 Redis 共享密钥环失败，当前节点将继续使用内存/数据库密钥: {}", ex.getMessage());
        }
    }

    private static byte[] createBasePointU9() {
        byte[] u9 = new byte[32];
        u9[0] = 9;
        return u9;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
