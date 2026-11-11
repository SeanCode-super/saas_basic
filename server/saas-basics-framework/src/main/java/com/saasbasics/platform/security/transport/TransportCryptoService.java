package com.saasbasics.platform.security.transport;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 权威混合传输加解密引擎：X25519 (RFC 7748) + HKDF-SHA256 (RFC 5869) + AES-256-GCM (NIST SP 800-38D)。
 * <p>
 * 安全保证：
 * 1. 每次请求通过客户端临时公钥实现前向安全 (Forward Secrecy)；
 * 2. 使用 HKDF 域分离严格区分请求密钥与响应密钥，杜绝反射攻击；
 * 3. AES-256-GCM 强制绑定包含请求方法、路径、Kid、时间戳与 Nonce 的 AAD，防跨接口替换与篡改；
 * 4. 校验时间戳窗口并通过 Redis SETNX（离线降级本地有界窗口）防重放。
 */
@Service
public class TransportCryptoService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] INFO_REQ = "saas-basics-req-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO_RESP = "saas-basics-resp-v1".getBytes(StandardCharsets.UTF_8);
    private static final String REDIS_NONCE_PREFIX = "saas:transport:nonce:";
    private static final int MAX_LOCAL_NONCES = 20_000;

    private final TransportCryptoKeyRing keyRing;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, Long> localNonceCache = new ConcurrentHashMap<>();

    public record DerivedKeySession(
            String keyId,
            long timestamp,
            String nonce,
            byte[] requestKey,
            byte[] responseKey
    ) {
        public void zeroize() {
            Arrays.fill(requestKey, (byte) 0);
        }
    }

    public TransportCryptoService(TransportCryptoKeyRing keyRing,
                                  ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.keyRing = keyRing;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    /**
     * 校验防重放并协商服务端与客户端的请求/响应定向 AES-256 密钥。
     */
    public DerivedKeySession establishSession(String keyId,
                                              String epkBase64,
                                              Long timestamp,
                                              String nonce,
                                              boolean enforceReplayGuard,
                                              int timestampSkewSeconds) {
        if (keyId == null || keyId.isBlank() || epkBase64 == null || epkBase64.isBlank()) {
            throw new TransportCryptoException(400, "TRANSPORT_HEADER_MISSING", "缺少必要的传输加密协商参数");
        }
        if (timestamp == null || nonce == null || nonce.isBlank()) {
            throw new TransportCryptoException(400, "TRANSPORT_REPLAY_META_MISSING", "缺少防重放时间戳或随机串");
        }

        if (enforceReplayGuard) {
            verifyAndRecordNonce(timestamp, nonce, timestampSkewSeconds);
        }

        TransportCryptoKeyRing.ServerKeyEntry keyEntry = keyRing.findKey(keyId);
        if (keyEntry == null) {
            throw new TransportCryptoException(428, "TRANSPORT_KEY_EXPIRED", "传输公钥已轮换或过期，请重新拉取最新公钥后重试");
        }

        byte[] clientEpkRaw;
        try {
            clientEpkRaw = Base64.getDecoder().decode(epkBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY", "临时公钥编码格式无效");
        }

        byte[] sharedSecret = computeX25519SharedSecret(keyEntry.privateKey(), clientEpkRaw);
        byte[] serverPubRaw = keyEntry.publicKeyRaw32();

        byte[] salt = ("saas-transport-v1|" + timestamp + "|" + nonce).getBytes(StandardCharsets.UTF_8);
        byte[] ikm = new byte[sharedSecret.length + clientEpkRaw.length + serverPubRaw.length];
        System.arraycopy(sharedSecret, 0, ikm, 0, sharedSecret.length);
        System.arraycopy(clientEpkRaw, 0, ikm, sharedSecret.length, clientEpkRaw.length);
        System.arraycopy(serverPubRaw, 0, ikm, sharedSecret.length + clientEpkRaw.length, serverPubRaw.length);

        byte[] prk = hkdfExtractSha256(salt, ikm);
        byte[] reqKey = hkdfExpandSha256(prk, INFO_REQ, 32);
        byte[] respKey = hkdfExpandSha256(prk, INFO_RESP, 32);

        Arrays.fill(sharedSecret, (byte) 0);
        Arrays.fill(ikm, (byte) 0);
        Arrays.fill(prk, (byte) 0);

        return new DerivedKeySession(keyId, timestamp, nonce, reqKey, respKey);
    }

    public byte[] decryptAesGcm(byte[] aesKey, String ivBase64, String cipherTextBase64, String aadString) {
        if (ivBase64 == null || ivBase64.isBlank() || cipherTextBase64 == null || cipherTextBase64.isBlank()) {
            throw new TransportCryptoException(400, "TRANSPORT_DECRYPT_FAILED", "密文或 IV 不能为空");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64.trim());
            if (iv.length != GCM_IV_BYTES) {
                throw new TransportCryptoException(400, "TRANSPORT_DECRYPT_FAILED", "GCM IV 长度必须为 12 字节");
            }
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64.trim());
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aadString != null && !aadString.isEmpty()) {
                cipher.updateAAD(aadString.getBytes(StandardCharsets.UTF_8));
            }
            return cipher.doFinal(cipherBytes);
        } catch (TransportCryptoException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new TransportCryptoException(400, "TRANSPORT_DECRYPT_FAILED", "传输密文认证解密失败或载荷被篡改");
        }
    }

    public TransportCryptoEnvelope.ResponseEnvelope encryptResponse(DerivedKeySession session,
                                                                    String method,
                                                                    String path,
                                                                    byte[] plaintextBytes) {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        long respTs = System.currentTimeMillis();
        String aad = buildAad("RESP", method, path, session.keyId(), respTs, session.nonce());
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(session.responseKey(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] cipherBytes = cipher.doFinal(plaintextBytes);
            return new TransportCryptoEnvelope.ResponseEnvelope(
                    true,
                    TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                    session.keyId(),
                    Base64.getEncoder().encodeToString(iv),
                    respTs,
                    Base64.getEncoder().encodeToString(cipherBytes)
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("响应报文 AES-256-GCM 加密失败", ex);
        }
    }

    public static String buildAad(String direction, String method, String path, String keyId, long timestamp, String nonce) {
        String normMethod = method == null ? "GET" : method.toUpperCase();
        String normPath = path == null ? "/" : path;
        return direction + "|" + normMethod + "|" + normPath + "|" + keyId + "|" + timestamp + "|" + nonce;
    }

    public void verifyAndRecordNonce(long timestamp, String nonce, int timestampSkewSeconds) {
        if (nonce.length() < 12 || nonce.length() > 128) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_NONCE", "防重放 Nonce 格式无效");
        }
        long now = System.currentTimeMillis();
        long skewMillis = Math.max(30L, timestampSkewSeconds) * 1000L;
        if (Math.abs(now - timestamp) > skewMillis) {
            throw new TransportCryptoException(428, "TRANSPORT_TIMESTAMP_EXPIRED", "请求时间戳超出允许偏差范围，请校对系统时间并重试");
        }

        long ttlSeconds = Math.max(60L, timestampSkewSeconds * 2L + 10L);
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                Boolean recorded = redis.opsForValue().setIfAbsent(REDIS_NONCE_PREFIX + nonce, "1", Duration.ofSeconds(ttlSeconds));
                if (Boolean.FALSE.equals(recorded)) {
                    throw new TransportCryptoException(400, "TRANSPORT_REPLAY_DETECTED", "检测到重复或重放加密请求");
                }
                return;
            } catch (TransportCryptoException ex) {
                throw ex;
            } catch (Exception ignored) {
                // Redis 异常时回退本地内存防重放表
            }
        }

        if (localNonceCache.size() > MAX_LOCAL_NONCES) {
            localNonceCache.entrySet().removeIf(entry -> entry.getValue() < now);
            if (localNonceCache.size() > MAX_LOCAL_NONCES) {
                localNonceCache.clear();
            }
        }
        long expiresAt = now + ttlSeconds * 1000L;
        Long existing = localNonceCache.putIfAbsent(nonce, expiresAt);
        if (existing != null && existing >= now) {
            throw new TransportCryptoException(400, "TRANSPORT_REPLAY_DETECTED", "检测到重复或重放加密请求");
        }
        if (existing != null) {
            localNonceCache.put(nonce, expiresAt);
        }
    }

    private byte[] computeX25519SharedSecret(PrivateKey serverPrivateKey, byte[] clientEpkRaw) {
        PublicKey clientPub = TransportCryptoKeyRing.decodeClientRawPublicKey(clientEpkRaw);
        try {
            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(serverPrivateKey);
            ka.doPhase(clientPub, true);
            byte[] secret = ka.generateSecret();
            int nonZero = 0;
            for (byte b : secret) {
                nonZero |= (b & 0xFF);
            }
            if (nonZero == 0) {
                throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY", "拒绝无效的低阶 X25519 公钥");
            }
            return secret;
        } catch (TransportCryptoException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw new TransportCryptoException(400, "TRANSPORT_INVALID_KEY", "X25519 密钥协商失败");
        }
    }

    public static byte[] hkdfExtractSha256(byte[] salt, byte[] ikm) {
        try {
            byte[] effectiveSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(effectiveSalt, HMAC_SHA256));
            return mac.doFinal(ikm);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HKDF-Extract 失败", ex);
        }
    }

    public static byte[] hkdfExpandSha256(byte[] prk, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(prk, HMAC_SHA256);
            byte[] result = new byte[length];
            byte[] t = new byte[0];
            int offset = 0;
            int counter = 1;
            while (offset < length) {
                mac.init(keySpec);
                mac.update(t);
                if (info != null) {
                    mac.update(info);
                }
                mac.update((byte) counter++);
                t = mac.doFinal();
                int toCopy = Math.min(t.length, length - offset);
                System.arraycopy(t, 0, result, offset, toCopy);
                offset += toCopy;
            }
            return result;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HKDF-Expand 失败", ex);
        }
    }
}
