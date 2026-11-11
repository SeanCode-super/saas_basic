package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class CaptchaAndTotpMfaServiceTest {

    private final StaticListableBeanFactory emptyFactory = new StaticListableBeanFactory();

    @Test
    void issuesAndVerifiesSingleUseServerCaptcha() {
        CaptchaService captchaService = new CaptchaService(emptyFactory.getBeanProvider(StringRedisTemplate.class));
        CaptchaService.CaptchaChallenge challenge = captchaService.issueChallenge();

        assertThat(challenge.captchaId()).isNotBlank();
        assertThat(challenge.imageDataUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(challenge.expiresInSeconds()).isEqualTo(180);

        String base64 = challenge.imageDataUrl().substring("data:image/svg+xml;base64,".length());
        String svg = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("<text [^>]*>([A-Z0-9])</text>").matcher(svg);
        StringBuilder code = new StringBuilder();
        while (matcher.find()) {
            code.append(matcher.group(1));
        }
        assertThat(code).hasSize(4);

        captchaService.verifyAndConsume(challenge.captchaId(), code.toString().toLowerCase());

        assertThatThrownBy(() -> captchaService.verifyAndConsume(challenge.captchaId(), code.toString()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AUTH_CAPTCHA_INVALID");
    }

    @Test
    void generatesTotpSecretAndPreventsSameStepReplay() {
        TotpMfaService totpService = new TotpMfaService(emptyFactory.getBeanProvider(StringRedisTemplate.class));
        String secret = totpService.generateSecret();
        assertThat(secret).hasSize(32);

        String otpUri = totpService.buildOtpAuthUri("Tianshu", "alice", secret);
        assertThat(otpUri)
                .startsWith("otpauth://totp/")
                .contains("secret=" + secret)
                .contains("digits=6")
                .contains("period=30");

        String currentCode = totpService.generateCode(secret, Instant.now());
        assertThat(currentCode).matches("^\\d{6}$");

        assertThat(totpService.verifyCode(secret, currentCode, 1001L)).isTrue();
        assertThat(totpService.verifyCode(secret, currentCode, 1001L)).isFalse();
        assertThat(totpService.verifyCode(secret, "000000", 1002L))
                .isEqualTo("000000".equals(currentCode));
        assertThat(totpService.generateRecoveryCodes()).hasSize(8);
    }
}
