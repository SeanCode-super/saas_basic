package com.saasbasics.platform.modules.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.auth.InitialPasswordIssuer.Rules;
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
    void issuesIndependentInitialPasswordsMatchingThePolicyAndStoredHash() {
        var policy = new PasswordPolicyEntity();
        policy.setMinLength(12);
        policy.setMaxLength(64);
        policy.setRequireUppercase(true);
        policy.setRequireLowercase(true);
        policy.setRequireNumber(true);
        policy.setRequireSpecial(true);
        Rules rules = new Rules(12, 64, true, true, true, true);
        String first = service.generateInitialPassword(rules);
        String second = service.generateInitialPassword(rules);

        service.validate(first, policy);
        service.validate(second, policy);
        assertThat(first.equals(second)).isFalse();
        String hash = service.hash(first);
        assertThat(service.needsRehash(hash)).isFalse();
        assertThat(service.matches(first, hash)).isTrue();
        assertThat(service.matches(second, hash)).isFalse();
    }

    @Test
    void followsTheTemplateLengthAndOnlyItsRequiredCharacterClasses() {
        for (int length : new int[]{12, 32}) {
            String issued = service.generateInitialPassword(new Rules(length, length, true, true, true, true));
            assertThat(issued.length()).isEqualTo(length);
            assertThat(issued.matches("(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).+")).isTrue();
        }
        String one = service.generateInitialPassword(new Rules(1, 1, false, false, true, false));
        assertThat(one.matches("[0-9]")).isTrue();
        for (Rules invalid : new Rules[]{new Rules(20, 12, false, false, false, false),
                new Rules(1, 3, true, true, true, true)}) {
            assertThatThrownBy(() -> service.generateInitialPassword(invalid))
                    .isInstanceOfSatisfying(BizException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PASSWORD_POLICY_VIOLATION"));
        }
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
