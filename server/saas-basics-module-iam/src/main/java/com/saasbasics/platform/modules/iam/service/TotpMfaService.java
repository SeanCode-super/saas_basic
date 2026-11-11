package com.saasbasics.platform.modules.iam.service;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 标准 RFC 6238 TOTP (HMAC-SHA1, 6 位, 30 秒步长, ±1 窗口容差) 多因子认证服务。
 */
@Service
public class TotpMfaService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final long STEP_SECONDS = 30L;
    private static final int WINDOW_STEPS = 1;
    private static final int DIGITS = 6;
    private static final String REDIS_USED_PREFIX = "saas:auth:mfa:used:";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Long> localReplayCache = new ConcurrentHashMap<>();

    public TotpMfaService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(8);
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        for (int i = 0; i < 8; i++) {
            StringBuilder sb = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public String buildOtpAuthUri(String issuer, String accountName, String base32Secret) {
        String safeIssuer = (issuer == null || issuer.isBlank()) ? "TianshuSaaS" : issuer.trim();
        String label = urlEncode(safeIssuer + ":" + accountName);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(base32Secret)
                + "&issuer=" + urlEncode(safeIssuer)
                + "&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }

    public String generateCode(String base32Secret, Instant instant) {
        byte[] key = decodeBase32(base32Secret);
        long counter = instant.getEpochSecond() / STEP_SECONDS;
        return computeTotp(key, counter);
    }

    public boolean verifyCode(String base32Secret, String code, Long userIdForReplayGuard) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String normalized = code.trim().replaceAll("\\s+", "");
        if (!normalized.matches("^\\d{6}$")) {
            return false;
        }
        byte[] key = decodeBase32(base32Secret);
        long currentCounter = Instant.now().getEpochSecond() / STEP_SECONDS;
        byte[] inputBytes = normalized.getBytes(StandardCharsets.UTF_8);

        for (long offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
            long step = currentCounter + offset;
            String expected = computeTotp(key, step);
            if (MessageDigest.isEqual(inputBytes, expected.getBytes(StandardCharsets.UTF_8))) {
                return markStepConsumed(userIdForReplayGuard, step, normalized);
            }
        }
        return false;
    }

    private boolean markStepConsumed(Long userId, long step, String code) {
        if (userId == null) {
            return true;
        }
        String replayKey = userId + ":" + step + ":" + code;
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(REDIS_USED_PREFIX + replayKey, "1", Duration.ofSeconds(120));
                return !Boolean.FALSE.equals(ok);
            } catch (Exception ignored) {
                // Fallback to local map
            }
        }
        long now = System.currentTimeMillis();
        if (localReplayCache.size() > 4096) {
            localReplayCache.entrySet().removeIf(e -> e.getValue() < now);
        }
        return localReplayCache.putIfAbsent(replayKey, now + 120_000L) == null;
    }

    private String computeTotp(byte[] key, long counter) {
        try {
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", ex);
        }
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET.charAt((buffer >> bitsLeft) & 0x1f));
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return out.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.trim().replace("=", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[(clean.length() * 5) / 8];
        int idx = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(clean.charAt(i));
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 char: " + clean.charAt(i));
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) ((buffer >> bitsLeft) & 0xff);
            }
        }
        return out;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
