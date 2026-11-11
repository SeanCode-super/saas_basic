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
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 传输加密 X25519 密钥环（支持平滑热轮换、宽限窗口保留历史私钥，以及基于 Vault + Redis 的多节点共享）。
 */
@Component
@EnableConfigurationProperties(TransportCryptoProperties.class)
public class TransportCryptoKeyRing {

    private static final Logger log = LoggerFactory.getLogger(TransportCryptoKeyRing.class);
    private static final String REDIS_ACTIVE_KID = "saas:transport:keyring:active_kid";
    private static final String REDIS_KEY_PREFIX = "saas:transport:keyring:entry:";
    private static final byte[] X25519_SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b656e032100");

    private final TransportCryptoProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<CredentialVaultService> vaultServiceProvider;

    private final ConcurrentHashMap<String, ServerKeyEntry> keyStore = new ConcurrentHashMap<>();
    private final Deque<String> keyOrder = new ConcurrentLinkedDeque<>();
    private volatile String activeKeyId;

    public record ServerKeyEntry(
            String keyId,
            PrivateKey privateKey,
            byte[] publicKeyRaw32,
            String publicKeyBase64,
            String fingerprintSha256,
            Instant issuedAt
    ) {
    }

    public TransportCryptoKeyRing(TransportCryptoProperties properties,
                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                  ObjectProvider<CredentialVaultService> vaultServiceProvider) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.vaultServiceProvider = vaultServiceProvider;
        initialize();
    }

    private synchronized void initialize() {
        if (loadActiveFromRedis()) {
            return;
        }
        rotateKeyPair();
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
        return loadEntryFromRedis(keyId);
    }

    public int loadedKeyCount() {
        return keyStore.size();
    }

    /**
     * 生成新的 X25519 密钥对并设为活跃密钥；同时保留最近若干个旧密钥以便平滑度过宽限期。
     */
    public synchronized ServerKeyEntry rotateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            KeyPair keyPair = kpg.generateKeyPair();
            byte[] spki = keyPair.getPublic().getEncoded();
            byte[] raw32 = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
            String pubB64 = Base64.getEncoder().encodeToString(raw32);
            String fingerprint = sha256Hex(raw32);
            Instant now = Instant.now();
            String kid = "k_" + Long.toString(now.toEpochMilli(), 36) + "_" + fingerprint.substring(0, 8);

            ServerKeyEntry entry = new ServerKeyEntry(kid, keyPair.getPrivate(), raw32, pubB64, fingerprint, now);
            registerEntry(entry);
            persistToRedis(entry);
            log.info("[TransportCrypto] X25519 传输密钥已轮换: kid={}, fp={}", kid, fingerprint.substring(0, 12));
            return entry;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("初始化 X25519 传输密钥对失败", ex);
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

    private void registerEntry(ServerKeyEntry entry) {
        keyStore.put(entry.keyId(), entry);
        keyOrder.remove(entry.keyId());
        keyOrder.addFirst(entry.keyId());
        this.activeKeyId = entry.keyId();

        int maxRetained = Math.max(2, properties.getMaxGraceKeys() + 1);
        while (keyOrder.size() > maxRetained) {
            String evicted = keyOrder.pollLast();
            if (evicted != null && !evicted.equals(this.activeKeyId)) {
                keyStore.remove(evicted);
            }
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
            ServerKeyEntry loaded = loadEntryFromRedis(kid);
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
                loadEntryFromRedis(remoteKid);
            }
        } catch (Exception ignored) {
            // 降级容错：Redis 短暂不可用时保持当前内存密钥正常工作
        }
    }

    private ServerKeyEntry loadEntryFromRedis(String kid) {
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
            String[] parts = decrypted.split("\\|", 3);
            if (parts.length < 3) {
                return null;
            }
            byte[] pkcs8 = Base64.getDecoder().decode(parts[0]);
            byte[] raw32 = Base64.getDecoder().decode(parts[1]);
            Instant issuedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
            PrivateKey priv = KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            ServerKeyEntry entry = new ServerKeyEntry(
                    kid,
                    priv,
                    raw32,
                    Base64.getEncoder().encodeToString(raw32),
                    sha256Hex(raw32),
                    issuedAt
            );
            registerEntry(entry);
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
            String rawPayload = Base64.getEncoder().encodeToString(entry.privateKey().getEncoded())
                    + "|" + entry.publicKeyBase64()
                    + "|" + entry.issuedAt().toEpochMilli();
            String sealed = vault.encrypt(rawPayload);
            long ttlHours = Math.max(24L, properties.getKeyRotationHours() * 2L);
            redis.opsForValue().set(REDIS_KEY_PREFIX + entry.keyId(), sealed, java.time.Duration.ofHours(ttlHours));
            redis.opsForValue().set(REDIS_ACTIVE_KID, entry.keyId(), java.time.Duration.ofHours(ttlHours));
        } catch (Exception ex) {
            log.warn("[TransportCrypto] 写入 Redis 共享密钥环失败，当前节点将继续使用内存密钥: {}", ex.getMessage());
        }
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
