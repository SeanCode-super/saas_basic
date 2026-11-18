package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @Test
    void verifiesSmartPowChallengeAndEscalatesToSliderOnRisk() throws Exception {
        CaptchaService captchaService = new CaptchaService(emptyFactory.getBeanProvider(StringRedisTemplate.class));
        String clientIp = "10.0.0.8";

        CaptchaService.SmartCaptchaChallenge smart = captchaService.issueSmartChallenge("AUTO", clientIp);
        assertThat(smart.challengeType()).isEqualTo("SMART");
        assertThat(smart.powSalt()).isNotBlank();
        assertThat(smart.powDifficulty()).isEqualTo(3);

        String validNonce = solvePow(smart.powSalt(), smart.powDifficulty());
        CaptchaService.VerifyChallengeResponse smartVerify = captchaService.verifyInteractiveChallenge(
                new CaptchaService.VerifyChallengeRequest(smart.captchaId(), validNonce, null, 120L, 1),
                clientIp
        );
        assertThat(smartVerify.verified()).isTrue();
        assertThat(smartVerify.passToken()).isNotBlank();

        captchaService.verifyAndConsume(smart.captchaId(), smartVerify.passToken());
        assertThatThrownBy(() -> captchaService.verifyAndConsume(smart.captchaId(), smartVerify.passToken()))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AUTH_CAPTCHA_INVALID");

        captchaService.recordAuthFailure(clientIp);
        CaptchaService.SmartCaptchaChallenge slider1 = captchaService.issueSmartChallenge("AUTO", clientIp);
        assertThat(slider1.challengeType()).isEqualTo("SLIDER");
        assertThat(slider1.backgroundImageDataUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(slider1.pieceImageDataUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(slider1.targetY()).isBetween(26, 90);

        assertThatThrownBy(() -> captchaService.verifyInteractiveChallenge(
                new CaptchaService.VerifyChallengeRequest(slider1.captchaId(), null, 5, 650L, 12),
                clientIp
        ))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AUTH_CAPTCHA_SLIDER_MISMATCH");

        CaptchaService.SmartCaptchaChallenge slider2 = captchaService.issueSmartChallenge("AUTO", clientIp);
        int expectedSlideX = extractExpectedSlideX(slider2.pieceImageDataUrl());
        CaptchaService.VerifyChallengeResponse sliderVerify = captchaService.verifyInteractiveChallenge(
                new CaptchaService.VerifyChallengeRequest(slider2.captchaId(), null, expectedSlideX, 820L, 16),
                clientIp
        );
        assertThat(sliderVerify.verified()).isTrue();
        captchaService.verifyAndConsume(slider2.captchaId(), sliderVerify.passToken());

        captchaService.clearAuthRisk(clientIp);
        CaptchaService.SmartCaptchaChallenge restored = captchaService.issueSmartChallenge("AUTO", clientIp);
        assertThat(restored.challengeType()).isEqualTo("SMART");
    }

    private static String solvePow(String salt, int difficulty) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String targetPrefix = "0".repeat(difficulty);
        for (long i = 0; i < 500_000L; i++) {
            String nonce = Long.toHexString(i);
            byte[] hash = digest.digest((salt + ":" + nonce).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            if (hex.toString().startsWith(targetPrefix)) {
                return nonce;
            }
        }
        throw new IllegalStateException("Failed to solve PoW within limit");
    }

    private static int extractExpectedSlideX(String pieceImageDataUrl) {
        String base64 = pieceImageDataUrl.substring("data:image/svg+xml;base64,".length());
        String svg = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("transform=\"translate\\((-?\\d+),\\s*0\\)\"").matcher(svg);
        assertThat(matcher.find()).isTrue();
        return -Integer.parseInt(matcher.group(1));
    }
}
