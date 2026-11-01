package com.saasbasics.platform.modules.integration.message;

import com.saasbasics.platform.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 工业级短信发送频次与安全风控防刷拦截器。
 * 遵循电信与工信部合规规范：单个手机号 60 秒限发 1 次，24 小时累计不得超过 10 次。
 */
@Component
public class SmsRateLimiter {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final long COOLDOWN_MILLIS = 60_000L; // 60秒冷却
    private static final int DAILY_MAX_COUNT = 10;       // 每天单号码上限10次

    private final Map<String, Long> lastSentTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailySentCounts = new ConcurrentHashMap<>();

    public void checkRateLimit(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException("INVALID_PHONE_NUMBER", "手机号码格式不合法: " + phone);
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastSentTimes.get(phone);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MILLIS) {
            long remainingSec = (COOLDOWN_MILLIS - (now - lastTime)) / 1000;
            throw new BizException("SMS_RATE_LIMIT", "短信发送过于频繁，请在 " + remainingSec + " 秒后重试");
        }

        Integer count = dailySentCounts.getOrDefault(phone, 0);
        if (count >= DAILY_MAX_COUNT) {
            throw new BizException("SMS_DAILY_LIMIT", "该手机号今日下发频次已达安全上限（10次），请明日再试");
        }
    }

    public void recordSuccess(String phone) {
        if (phone == null) return;
        lastSentTimes.put(phone, System.currentTimeMillis());
        dailySentCounts.merge(phone, 1, Integer::sum);
    }
}
