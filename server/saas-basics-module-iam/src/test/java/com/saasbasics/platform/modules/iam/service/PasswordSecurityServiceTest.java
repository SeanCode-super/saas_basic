package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PasswordSecurityServiceTest {

    private final PasswordSecurityService service = new PasswordSecurityService();

    @Test
    void hashesWithSaltedPbkdf2AndSupportsLegacySha256RehashDetection() throws Exception {
        String h1 = service.hash("Tianshu@2026");
        String h2 = service.hash("Tianshu@2026");

        assertThat(h1).startsWith("pbkdf2_sha256$60000$");
        assertThat(h1).isNotEqualTo(h2);
        assertThat(service.matches("Tianshu@2026", h1)).isTrue();
        assertThat(service.matches("Wrong@2026", h1)).isFalse();
        assertThat(service.needsRehash(h1)).isFalse();

        byte[] digest = MessageDigest.getInstance("SHA-256").digest("Legacy@123".getBytes(StandardCharsets.UTF_8));
        String legacyHex = HexFormat.of().formatHex(digest);
        assertThat(service.matches("Legacy@123", legacyHex)).isTrue();
        assertThat(service.matches("Legacy@124", legacyHex)).isFalse();
        assertThat(service.needsRehash(legacyHex)).isTrue();
    }

    @Test
    void validatesPasswordAgainstPolicyRules() {
        PasswordPolicyEntity policy = new PasswordPolicyEntity();
        policy.setMinLength(10);
        policy.setMaxLength(32);
        policy.setRequireUppercase(true);
        policy.setRequireLowercase(true);
        policy.setRequireNumber(true);
        policy.setRequireSpecial(true);

        service.validate("ValidPass#2026", policy);

        assertThatThrownBy(() -> service.validate("Short#1", policy))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("PASSWORD_POLICY_VIOLATION");

        assertThatThrownBy(() -> service.validate("nouppercase123#", policy))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.validate("NOSPECIALCHAR123a", policy))
                .isInstanceOf(BizException.class);
    }
}
