package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.charset.StandardCharsets;
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
 * 服务端一次性图形验证码服务：生成干扰 SVG 并托管于 Redis（离线降级为有界本地缓存），校验后立即销毁防重放。
 */
@Service
public class CaptchaService {

    private static final String REDIS_PREFIX = "saas:auth:captcha:";
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int TTL_SECONDS = 180;
    private static final int MAX_LOCAL_CACHE = 4096;

    public record CaptchaChallenge(
            String captchaId,
            String imageDataUrl,
            int expiresInSeconds
    ) {
    }

    private record LocalCaptchaEntry(String answerUpper, long expiresAtMs) {
    }

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, LocalCaptchaEntry> localCache = new ConcurrentHashMap<>();

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
        storeAnswer(captchaId, code);
        String svg = renderSvg(code);
        String dataUrl = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaChallenge(captchaId, dataUrl, TTL_SECONDS);
    }

    public void verifyAndConsume(String captchaId, String userInput) {
        if (captchaId == null || captchaId.isBlank() || userInput == null || userInput.isBlank()) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "请输入有效的图形验证码");
        }
        String expected = consumeAnswer(captchaId.trim());
        if (expected == null || !expected.equalsIgnoreCase(userInput.trim())) {
            throw new BizException("AUTH_CAPTCHA_INVALID", "图形验证码错误或已过期，请刷新重试");
        }
    }

    private void storeAnswer(String captchaId, String answer) {
        String upper = answer.toUpperCase(Locale.ROOT);
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis != null) {
            try {
                redis.opsForValue().set(REDIS_PREFIX + captchaId, upper, Duration.ofSeconds(TTL_SECONDS));
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
        localCache.put(captchaId, new LocalCaptchaEntry(upper, System.currentTimeMillis() + TTL_SECONDS * 1000L));
    }

    private String consumeAnswer(String captchaId) {
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
        return local.answerUpper();
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
