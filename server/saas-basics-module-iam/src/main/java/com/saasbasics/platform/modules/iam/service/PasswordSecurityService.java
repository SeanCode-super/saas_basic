package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;

/**
 * 企业级密码散列与基线策略校验服务：
 * 默认采用加盐 PBKDF2WithHmacSHA256（60,000 次迭代），并向下兼容历史 SHA-256 凭据，支持登录阶段平滑自动升级。
 */
@Service
public class PasswordSecurityService {

    private static final String PBKDF2_PREFIX = "pbkdf2_sha256$";
    private static final int PBKDF2_ITERATIONS = 60_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern NUMBER = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = pbkdf2(rawPassword, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        return PBKDF2_PREFIX
                + PBKDF2_ITERATIONS
                + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public boolean needsRehash(String storedHash) {
        return storedHash != null && !storedHash.startsWith(PBKDF2_PREFIX);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        if (passwordHash.startsWith(PBKDF2_PREFIX)) {
            return matchesPbkdf2(rawPassword, passwordHash);
        }
        // Smooth compatibility for legacy unsalted SHA-256 hashes prior to PBKDF2 upgrade
        byte[] actual = sha256HexBytes(rawPassword);
        byte[] expected = passwordHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    public void validate(String rawPassword, PasswordPolicyEntity policy) {
        if (policy == null) {
            return;
        }
        int minLength = policy.getMinLength() == null ? 8 : policy.getMinLength();
        int maxLength = policy.getMaxLength() == null ? 64 : Math.max(policy.getMaxLength(), minLength);
        if (rawPassword == null || rawPassword.length() < minLength || rawPassword.length() > maxLength) {
            throw new BizException("PASSWORD_POLICY_VIOLATION",
                    "密码长度须在 " + minLength + " - " + maxLength + " 位之间");
        }
        if (Boolean.TRUE.equals(policy.getRequireUppercase()) && !UPPERCASE.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "密码必须包含大写字母");
        }
        if (Boolean.TRUE.equals(policy.getRequireLowercase()) && !LOWERCASE.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "密码必须包含小写字母");
        }
        if (Boolean.TRUE.equals(policy.getRequireNumber()) && !NUMBER.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "密码必须包含数字");
        }
        if (Boolean.TRUE.equals(policy.getRequireSpecial()) && !SPECIAL.matcher(rawPassword).matches()) {
            throw new BizException("PASSWORD_POLICY_VIOLATION", "密码必须包含特殊字符（例如 @#$%!）");
        }
    }

    private boolean matchesPbkdf2(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(rawPassword, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", ex);
        }
    }

    private byte[] sha256HexBytes(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
            return hex.getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
