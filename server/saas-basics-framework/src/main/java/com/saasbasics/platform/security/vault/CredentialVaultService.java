package com.saasbasics.platform.security.vault;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 企业级凭据保险箱（AES-256-GCM 认证加密引擎）。
 * <p>
 * 密文信封格式：enc:{keyVersion}:{base64(12-byte IV || ciphertext+128bit-GCM-tag)}
 * 兼容平滑升级：读取时若遇到未带 enc: 前缀的历史明文，按明文透传，配合 rotate() 可零停机批处理加密升级。
 */
@Service
@EnableConfigurationProperties(VaultCryptoProperties.class)
public class CredentialVaultService {

    private static final Logger log = LoggerFactory.getLogger(CredentialVaultService.class);
    private static final String ENVELOPE_PREFIX = "enc:";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final VaultCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialVaultService(VaultCryptoProperties properties) {
        this.properties = properties;
        if (isUsingDefaultMasterKey()) {
            log.warn("[CredentialVault] 当前使用本地开发默认主密钥，生产环境请配置 SAAS_VAULT_MASTER_KEY");
        }
    }

    public boolean isUsingDefaultMasterKey() {
        String key = properties.getMasterKey();
        return key == null || key.isBlank() || VaultCryptoProperties.DEV_DEFAULT_MASTER_KEY.equals(key);
    }

    public String activeKeyVersion() {
        String v = properties.getActiveKeyVersion();
        return (v == null || v.isBlank()) ? "v1" : v.trim();
    }

    public boolean isEncrypted(String value) {
        if (value == null || !value.startsWith(ENVELOPE_PREFIX)) {
            return false;
        }
        int secondColon = value.indexOf(':', ENVELOPE_PREFIX.length());
        return secondColon > ENVELOPE_PREFIX.length() && secondColon < value.length() - 1;
    }

    public String extractKeyVersion(String value) {
        if (!isEncrypted(value)) {
            return null;
        }
        int secondColon = value.indexOf(':', ENVELOPE_PREFIX.length());
        return value.substring(ENVELOPE_PREFIX.length(), secondColon);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        String trimmed = plaintext.trim();
        if (!properties.isEnabled()) {
            return trimmed;
        }
        if (isEncrypted(trimmed)) {
            return trimmed;
        }
        return sealWithVersion(trimmed, activeKeyVersion(), properties.getMasterKey());
    }

    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        String trimmed = storedValue.trim();
        if (!isEncrypted(trimmed)) {
            // Smooth compatibility for legacy plaintext secrets prior to vault migration
            return trimmed;
        }
        int secondColon = trimmed.indexOf(':', ENVELOPE_PREFIX.length());
        String version = trimmed.substring(ENVELOPE_PREFIX.length(), secondColon);
        String payload = trimmed.substring(secondColon + 1);
        String keyMaterial = resolveKeyMaterial(version);
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new BizException("VAULT_KEY_NOT_FOUND", "未找到对应密钥版本的解密主密钥: " + version);
        }
        try {
            byte[] combined = Base64.getDecoder().decode(payload);
            if (combined.length <= GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new BizException("VAULT_DECRYPT_FAILED", "密文格式损坏或长度不足");
            }
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveAesKey(keyMaterial), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (BizException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new BizException("VAULT_DECRYPT_FAILED", "凭据解密失败或密文已被篡改");
        }
    }

    /**
     * 密钥轮换或明文升级：解密后使用当前激活版本密钥与全新随机 IV 重新封装。
     */
    public String rotate(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }
        String plaintext = decrypt(storedValue);
        if (!properties.isEnabled()) {
            return plaintext;
        }
        return sealWithVersion(plaintext, activeKeyVersion(), properties.getMasterKey());
    }

    public String mask(String storedOrPlain) {
        if (storedOrPlain == null || storedOrPlain.isBlank()) {
            return "未配置";
        }
        String raw;
        try {
            raw = decrypt(storedOrPlain);
        } catch (BizException ex) {
            return "enc:****(不可读)";
        }
        if (raw == null || raw.isBlank()) {
            return "未配置";
        }
        String clean = raw.trim();
        if (clean.length() <= 8) {
            return "******";
        }
        String prefix = clean.startsWith("sk-") ? "sk-" : clean.substring(0, 3);
        String suffix = clean.substring(clean.length() - 4);
        return prefix + "****" + suffix;
    }

    public String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    private String sealWithVersion(String plaintext, String version, String keyMaterial) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveAesKey(keyMaterial), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return ENVELOPE_PREFIX + version + ":" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new BizException("VAULT_ENCRYPT_FAILED", "凭据加密失败: " + ex.getMessage());
        }
    }

    private String resolveKeyMaterial(String version) {
        if (Objects.equals(activeKeyVersion(), version)) {
            return properties.getMasterKey();
        }
        if (properties.getPreviousKeys() != null) {
            String previous = properties.getPreviousKeys().get(version);
            if (previous != null && !previous.isBlank()) {
                return previous;
            }
        }
        // Fallback to active master key if version string matched default
        if ("v1".equals(version)) {
            return properties.getMasterKey();
        }
        return null;
    }

    private SecretKey deriveAesKey(String material) throws GeneralSecurityException {
        String effective = (material == null || material.isBlank())
                ? VaultCryptoProperties.DEV_DEFAULT_MASTER_KEY
                : material.trim();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha256.digest(effective.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
