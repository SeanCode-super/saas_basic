package com.saasbasics.platform.security.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialVaultServiceTest {

    @Test
    void encryptsWithDistinctRandomIvsAndRoundTripsSafely() {
        VaultCryptoProperties props = new VaultCryptoProperties();
        props.setMasterKey("enterprise-vault-prod-master-secret-2026");
        CredentialVaultService vault = new CredentialVaultService(props);

        String cipher1 = vault.encrypt("sk-deepseek-secret-token-123456");
        String cipher2 = vault.encrypt("sk-deepseek-secret-token-123456");

        assertThat(cipher1).startsWith("enc:v1:");
        assertThat(cipher2).startsWith("enc:v1:");
        assertThat(cipher1).isNotEqualTo(cipher2);
        assertThat(vault.isEncrypted(cipher1)).isTrue();
        assertThat(vault.decrypt(cipher1)).isEqualTo("sk-deepseek-secret-token-123456");
        assertThat(vault.decrypt(cipher2)).isEqualTo("sk-deepseek-secret-token-123456");
        assertThat(vault.encrypt(cipher1)).isEqualTo(cipher1); // idempotent on ciphertext
        assertThat(vault.mask(cipher1)).isEqualTo("sk-****3456");
    }

    @Test
    void detectsTamperedCiphertextViaGcmAuthenticationTag() {
        VaultCryptoProperties props = new VaultCryptoProperties();
        props.setMasterKey("enterprise-vault-prod-master-secret-2026");
        CredentialVaultService vault = new CredentialVaultService(props);

        String cipher = vault.encrypt("sk-super-sensitive-key");
        char last = cipher.charAt(cipher.length() - 1);
        char mutated = (last == 'A') ? 'B' : 'A';
        String tampered = cipher.substring(0, cipher.length() - 1) + mutated;

        assertThatThrownBy(() -> vault.decrypt(tampered))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("VAULT_DECRYPT_FAILED");
    }

    @Test
    void supportsLegacyPlaintextCompatibilityAndZeroDowntimeKeyRotation() {
        VaultCryptoProperties oldProps = new VaultCryptoProperties();
        oldProps.setActiveKeyVersion("v1");
        oldProps.setMasterKey("old-master-key-v1");
        CredentialVaultService oldVault = new CredentialVaultService(oldProps);
        String v1Cipher = oldVault.encrypt("sk-tenant-byok-secret-9988");

        VaultCryptoProperties newProps = new VaultCryptoProperties();
        newProps.setActiveKeyVersion("v2");
        newProps.setMasterKey("new-master-key-v2");
        newProps.setPreviousKeys(Map.of("v1", "old-master-key-v1"));
        CredentialVaultService rotatedVault = new CredentialVaultService(newProps);

        String rotatedCipher = rotatedVault.rotate(v1Cipher);
        String upgradedLegacy = rotatedVault.rotate("sk-legacy-plaintext-1122");

        assertThat(rotatedCipher).startsWith("enc:v2:");
        assertThat(rotatedVault.decrypt(rotatedCipher)).isEqualTo("sk-tenant-byok-secret-9988");
        assertThat(upgradedLegacy).startsWith("enc:v2:");
        assertThat(rotatedVault.decrypt(upgradedLegacy)).isEqualTo("sk-legacy-plaintext-1122");
    }
}
