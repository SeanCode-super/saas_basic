package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 服务端自适应人机核验服务（天枢智盾）：
 * 1) 低风险默认签发一次性 SHA-256 工作量证明挑战（SMART 无感/一键打勾验证）
 * 2) 检测到登录失败或高风险 IP 时自动升级为一次性 SVG 缺口滑动拼图挑战（SLIDER）
 * 3) 同时向下兼容传统四位字符图形验证码接口，全部校验结果一次性消费防重放。
 */
@Service
public class CaptchaService {

    private static final String REDIS_PREFIX = "saas:auth:captcha:";
    private static final String REDIS_RISK_PREFIX = "saas:auth:captcha:risk:";
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int TTL_SECONDS = 180;
    private static final int PASS_TTL_SECONDS = 120;
    private static final int RISK_TTL_SECONDS = 600;
    private static final int SMART_POW_DIFFICULTY = 3;
    private static final int SLIDER_TOLERANCE_PX = 6;
    private static final int PUZZLE_WIDTH = 312;
    private static final int PUZZLE_HEIGHT = 156;
    private static final int PIECE_STRIP_WIDTH = 60;
    private static final int PIECE_LEFT_PAD = 4;
    private static final int MAX_LOCAL_CACHE = 4096;

    public record CaptchaChallenge(
            String captchaId,
            String imageDataUrl,
            int expiresInSeconds
    ) {
    }

    public record SmartCaptchaChallenge(
            String captchaId,
            String challengeType,
            String powSalt,
            int powDifficulty,
            String backgroundImageDataUrl,
            String pieceImageDataUrl,
            Integer targetY,
            int expiresInSeconds
    ) {
    }

    public record VerifyChallengeRequest(
            String captchaId,
            String nonce,
            Integer slideX,
            Long durationMs,
            Integer trailCount
    ) {
    }

    public record VerifyChallengeResponse(
            boolean verified,
            String passToken
    ) {
    }

    private record LocalCaptchaEntry(String value, long expiresAtMs) {
    }

    private record LocalRiskEntry(int count, long expiresAtMs) {
    }

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, LocalCaptchaEntry> localCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalRiskEntry> localRiskCache = new ConcurrentHashMap<>();

    public CaptchaService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public CaptchaChallenge issueChallenge() {
        StringBuilder codeBuilder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            codeBuilder.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String code = codeBuilder.toString();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        storeValue(captchaId, code.toUpperCase(Locale.ROOT), TTL_SECONDS);
        String svg = renderSvg(code);
        String dataUrl = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaChallenge(captchaId, dataUrl, TTL_SECONDS);
    }

    public SmartCaptchaChallenge issueSmartChallenge(String preferredMode, String clientIp) {
        boolean forceSlider = "SLIDER".equalsIgnoreCase(preferredMode);
        boolean highRisk = isHighRisk(clientIp);
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        if (forceSlider || highRisk) {
            int targetX = 96 + random.nextInt(141);
            int targetY = 26 + random.nextInt(65);
            int expectedSlideX = targetX - PIECE_LEFT_PAD;
            long sceneSeed = random.nextLong();
            String scene = renderPuzzleScene(sceneSeed);
            String cutoutPath = jigsawPath(targetX, targetY);
            String piecePath = jigsawPath(PIECE_LEFT_PAD, targetY);

            String bgSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + PUZZLE_WIDTH
                    + "\" height=\"" + PUZZLE_HEIGHT + "\" viewBox=\"0 0 " + PUZZLE_WIDTH + " " + PUZZLE_HEIGHT + "\">"
                    + scene
                    + "<path d=\"" + cutoutPath + "\" fill=\"rgba(4,8,22,0.74)\" stroke=\"rgba(255,255,255,0.78)\" stroke-width=\"1.6\"/>"
                    + "</svg>";

            String clipId = "jc_" + captchaId.substring(0, 8);
            String pieceSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + PIECE_STRIP_WIDTH
                    + "\" height=\"" + PUZZLE_HEIGHT + "\" viewBox=\"0 0 " + PIECE_STRIP_WIDTH + " " + PUZZLE_HEIGHT + "\">"
                    + "<defs><clipPath id=\"" + clipId + "\"><path d=\"" + piecePath + "\"/></clipPath></defs>"
                    + "<g clip-path=\"url(#" + clipId + ")\"><g transform=\"translate(" + (PIECE_LEFT_PAD - targetX) + ", 0)\">"
                    + scene
                    + "</g></g>"
                    + "<path d=\"" + piecePath + "\" fill=\"none\" stroke=\"rgba(56,189,248,0.95)\" stroke-width=\"2.2\"/>"
                    + "<path d=\"" + piecePath + "\" fill=\"none\" stroke=\"rgba(255,255,255,0.88)\" stroke-width=\"0.9\"/>"
                    + "</svg>";

            storeValue(captchaId, "SLIDER:" + expectedSlideX, TTL_SECONDS);
            return new SmartCaptchaChallenge(
                    captchaId,
                    "SLIDER",
                    null,
                    0,
                    toSvgDataUrl(bgSvg),
                    toSvgDataUrl(pieceSvg),
                    targetY,
                    TTL_SECONDS
            );
        }

        String powSalt = randomHex(16);
        storeValue(captchaId, "SMART:" + powSalt + ":" + SMART_POW_DIFFICULTY, TTL_SECONDS);
        return new SmartCaptchaChallenge(
                captchaId,
                "SMART",
                powSalt,
                SMART_POW_DIFFICULTY,
                null,
                null,
                null,
                TTL_SECONDS
        );
    }

    public VerifyChallengeResponse verifyInteractiveChallenge(VerifyChallengeRequest request, String clientIp) {
        if (request == null || request.captchaId() == null || request.captchaId().isBlank()) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "安全验证参数缺失，请重试");
        }
        String captchaId = request.captchaId().trim();
        String stored = consumeValue(captchaId);
        if (stored == null) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "安全验证已过期，请重新核验");
        }

        if (stored.startsWith("SMART:")) {
            String[] parts = stored.split(":");
            if (parts.length != 3) {
                throw new BizException("AUTH_CAPTCHA_INVALID", "安全挑战状态异常，请重试");
            }
            String salt = parts[1];
            int difficulty = Integer.parseInt(parts[2]);
            String nonce = request.nonce() != null ? request.nonce().trim() : "";
            if (nonce.isEmpty() || !matchesPowDifficulty(salt, nonce, difficulty)) {
                recordAuthFailure(clientIp);
                throw new BizException("AUTH_CAPTCHA_UPGRADE_SLIDER", "安全环境波动，请完成滑动拼图验证");
            }
        } else if (stored.startsWith("SLIDER:")) {
            int expectedSlideX = Integer.parseInt(stored.substring("SLIDER:".length()));
            boolean xMatched = request.slideX() != null && Math.abs(request.slideX() - expectedSlideX) <= SLIDER_TOLERANCE_PX;
            boolean validDuration = request.durationMs() != null && request.durationMs() >= 80L && request.durationMs() <= 30000L;
            boolean hasTrail = request.trailCount() != null && request.trailCount() >= 2;
            if (!xMatched || !validDuration || !hasTrail) {
                recordAuthFailure(clientIp);
                throw new BizException("AUTH_CAPTCHA_SLIDER_MISMATCH", "拼图位置未对齐，请重新拖动");
            }
        } else {
            throw new BizException("AUTH_CAPTCHA_INVALID", "安全验证状态无效，请重试");
        }

        String passToken = randomHex(16);
        storeValue(captchaId, "PASS:" + passToken, PASS_TTL_SECONDS);
        return new VerifyChallengeResponse(true, passToken);
    }

    public void verifyAndConsume(String captchaId, String userInput) {
        if (captchaId == null || captchaId.isBlank() || userInput == null || userInput.isBlank()) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "请先完成安全验证");
        }
        String expected = consumeValue(captchaId.trim());
        if (expected == null) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "安全验证错误或已过期，请重试");
        }
        if (expected.startsWith("PASS:")) {
            String expectedToken = expected.substring("PASS:".length());
            if (!expectedToken.equals(userInput.trim())) {
                throw new BizException("AUTH_CAPTCHA_INVALID", "安全验证凭证无效，请重新核验");
            }
            return;
        }
        if (!expected.equalsIgnoreCase(userInput.trim())) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "图形验证码错误或已过期，请刷新重试");
        }
    }

    public void recordAuthFailure(String clientIp) {
        String key = normalizeRiskKey(clientIp);
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.opsForValue().increment(REDIS_RISK_PREFIX + key);
                redis.expire(REDIS_RISK_PREFIX + key, Duration.ofSeconds(RISK_TTL_SECONDS));
                return;
            } catch (Exception ignored) {
                // Redis 短暂异常时降级至本地计数
            }
        }
        long now = System.currentTimeMillis();
        localRiskCache.compute(key, (k, existing) -> {
            int nextCount = (existing == null || existing.expiresAtMs() < now) ? 1 : existing.count() + 1;
            return new LocalRiskEntry(nextCount, now + RISK_TTL_SECONDS * 1000L);
        });
    }

    public void clearAuthRisk(String clientIp) {
        String key = normalizeRiskKey(clientIp);
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.delete(REDIS_RISK_PREFIX + key);
            } catch (Exception ignored) {
                // ignore
            }
        }
        localRiskCache.remove(key);
    }

    public boolean isHighRisk(String clientIp) {
        String key = normalizeRiskKey(clientIp);
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                String val = redis.opsForValue().get(REDIS_RISK_PREFIX + key);
                if (val != null) {
                    return Integer.parseInt(val) >= 1;
                }
                return false;
            } catch (Exception ignored) {
                // Fallback to local risk cache
            }
        }
        LocalRiskEntry entry = localRiskCache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAtMs() < System.currentTimeMillis()) {
            localRiskCache.remove(key, entry);
            return false;
        }
        return entry.count() >= 1;
    }

    private String normalizeRiskKey(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private void storeValue(String captchaId, String value, int ttlSeconds) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.opsForValue().set(REDIS_PREFIX + captchaId, value, Duration.ofSeconds(ttlSeconds));
                return;
            } catch (Exception ignored) {
                // Redis 短暂异常时降级至本地缓存
            }
        }
        if (localCache.size() >= MAX_LOCAL_CACHE) {
            long now = System.currentTimeMillis();
            localCache.entrySet().removeIf(e -> e.getValue().expiresAtMs() < now);
            if (localCache.size() >= MAX_LOCAL_CACHE) {
                localCache.clear();
            }
        }
        localCache.put(captchaId, new LocalCaptchaEntry(value, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    private String consumeValue(String captchaId) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                String value = redis.opsForValue().getAndDelete(REDIS_PREFIX + captchaId);
                if (value != null) {
                    return value;
                }
            } catch (Exception ignored) {
                // Fallback to local cache
            }
        }
        LocalCaptchaEntry local = localCache.remove(captchaId);
        if (local == null || local.expiresAtMs() < System.currentTimeMillis()) {
            return null;
        }
        return local.value();
    }

    private boolean matchesPowDifficulty(String salt, String nonce, int difficulty) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + ":" + nonce).getBytes(StandardCharsets.UTF_8));
            String hex = toHex(hash);
            String prefix = "0".repeat(Math.max(1, difficulty));
            return hex.startsWith(prefix);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return toHex(bytes);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private String toSvgDataUrl(String svg) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String jigsawPath(double x, double y) {
        double s = 44.0;
        double r = 7.0;
        double midX = x + s / 2.0;
        double midY = y + s / 2.0;
        return String.format(Locale.ROOT,
                "M %.1f %.1f L %.1f %.1f A %.1f %.1f 0 1 1 %.1f %.1f L %.1f %.1f L %.1f %.1f A %.1f %.1f 0 1 1 %.1f %.1f L %.1f %.1f L %.1f %.1f L %.1f %.1f Z",
                x, y,
                midX - r, y, r, r, midX + r, y, x + s, y,
                x + s, midY - r, r, r, x + s, midY + r, x + s, y + s,
                x, y + s,
                x, y);
    }

    private String renderPuzzleScene(long seed) {
        java.util.Random seeded = new java.util.Random(seed);
        String[][] palettes = {
                {"#091128", "#1e1b4b", "#0ea5e9", "#6366f1"},
                {"#071722", "#113247", "#14b8a6", "#38bdf8"},
                {"#161224", "#2e1f47", "#f59e0b", "#818cf8"},
                {"#06142e", "#172554", "#3b82f6", "#22d3ee"}
        };
        String[] pal = palettes[Math.abs(seeded.nextInt()) % palettes.length];
        String uid = Long.toHexString(seed & 0xFFFFFFFFL);
        StringBuilder sb = new StringBuilder();
        sb.append("<defs>")
                .append("<linearGradient id=\"sky_").append(uid).append("\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">")
                .append("<stop offset=\"0%\" stop-color=\"").append(pal[0]).append("\"/>")
                .append("<stop offset=\"100%\" stop-color=\"").append(pal[1]).append("\"/>")
                .append("</linearGradient>")
                .append("<radialGradient id=\"orb_").append(uid).append("\" cx=\"32%\" cy=\"34%\" r=\"62%\">")
                .append("<stop offset=\"0%\" stop-color=\"").append(pal[2]).append("\" stop-opacity=\"0.42\"/>")
                .append("<stop offset=\"55%\" stop-color=\"").append(pal[3]).append("\" stop-opacity=\"0.18\"/>")
                .append("<stop offset=\"100%\" stop-color=\"").append(pal[0]).append("\" stop-opacity=\"0\"/>")
                .append("</radialGradient>")
                .append("</defs>")
                .append("<rect width=\"").append(PUZZLE_WIDTH).append("\" height=\"").append(PUZZLE_HEIGHT)
                .append("\" fill=\"url(#sky_").append(uid).append(")\"/>")
                .append("<rect width=\"").append(PUZZLE_WIDTH).append("\" height=\"").append(PUZZLE_HEIGHT)
                .append("\" fill=\"url(#orb_").append(uid).append(")\"/>");

        int ringX = 72 + seeded.nextInt(168);
        int ringY = 44 + seeded.nextInt(68);
        sb.append("<circle cx=\"").append(ringX).append("\" cy=\"").append(ringY).append("\" r=\"46\" fill=\"none\" stroke=\"")
                .append(pal[2]).append("\" stroke-opacity=\"0.42\" stroke-width=\"1.2\" stroke-dasharray=\"4 5\"/>")
                .append("<circle cx=\"").append(ringX).append("\" cy=\"").append(ringY).append("\" r=\"26\" fill=\"none\" stroke=\"")
                .append(pal[3]).append("\" stroke-opacity=\"0.55\" stroke-width=\"1\"/>");

        for (int i = 0; i < 6; i++) {
            int x1 = seeded.nextInt(PUZZLE_WIDTH);
            int y1 = seeded.nextInt(PUZZLE_HEIGHT);
            int x2 = seeded.nextInt(PUZZLE_WIDTH);
            int y2 = seeded.nextInt(PUZZLE_HEIGHT);
            int x3 = seeded.nextInt(PUZZLE_WIDTH);
            int y3 = seeded.nextInt(PUZZLE_HEIGHT);
            String color = (i % 2 == 0) ? pal[2] : pal[3];
            sb.append("<polygon points=\"").append(x1).append(",").append(y1).append(" ")
                    .append(x2).append(",").append(y2).append(" ").append(x3).append(",").append(y3)
                    .append("\" fill=\"").append(color).append("\" fill-opacity=\"0.14\" stroke=\"")
                    .append(color).append("\" stroke-opacity=\"0.28\" stroke-width=\"0.9\"/>");
        }
        for (int i = 0; i < 18; i++) {
            int cx = 10 + seeded.nextInt(PUZZLE_WIDTH - 20);
            int cy = 10 + seeded.nextInt(PUZZLE_HEIGHT - 20);
            double radius = 1.1 + seeded.nextDouble() * 2.1;
            sb.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy).append("\" r=\"")
                    .append(String.format(Locale.ROOT, "%.1f", radius))
                    .append("\" fill=\"#ffffff\" fill-opacity=\"")
                    .append(String.format(Locale.ROOT, "%.2f", 0.35 + seeded.nextDouble() * 0.5))
                    .append("\"/>");
        }
        int waveY = 92 + seeded.nextInt(32);
        sb.append("<path d=\"M0 ").append(waveY).append(" Q 78 ").append(waveY - 26).append(", 156 ").append(waveY)
                .append(" T 312 ").append(waveY - 8).append("\" fill=\"none\" stroke=\"")
                .append(pal[2]).append("\" stroke-width=\"1.6\" stroke-opacity=\"0.58\"/>");
        return sb.toString();
    }

    private String renderSvg(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"40\" viewBox=\"0 0 120 40\">");
        sb.append("<defs><linearGradient id=\"g\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">");
        sb.append("<stop offset=\"0%\" stop-color=\"#f1f5f9\"/><stop offset=\"100%\" stop-color=\"#dbeafe\"/>");
        sb.append("</linearGradient></defs>");
        sb.append("<rect width=\"120\" height=\"40\" rx=\"8\" fill=\"url(#g)\"/>");
        for (int i = 0; i < 4; i++) {
            int x1 = random.nextInt(30);
            int y1 = 5 + random.nextInt(30);
            int x2 = 90 + random.nextInt(30);
            int y2 = 5 + random.nextInt(30);
            sb.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1)
                    .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
                    .append("\" stroke=\"#94a3b8\" stroke-width=\"1.2\" stroke-opacity=\"0.55\"/>");
        }
        String[] colors = {"#1d4ed8", "#0f766e", "#334155", "#7c3aed"};
        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * 25;
            int y = 26 + (random.nextInt(7) - 3);
            int rot = random.nextInt(25) - 12;
            sb.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" transform=\"rotate(").append(rot).append(" ").append(x).append(" ").append(y).append(")\"")
                    .append(" font-family=\"monospace\" font-size=\"20\" font-weight=\"700\" fill=\"")
                    .append(colors[i % colors.length]).append("\">")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }
}
