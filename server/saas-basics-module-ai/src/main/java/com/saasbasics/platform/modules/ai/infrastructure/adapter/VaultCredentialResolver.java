package com.saasbasics.platform.modules.ai.infrastructure.adapter;

import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import org.springframework.stereotype.Component;

@Component
public class VaultCredentialResolver implements CredentialResolver {

    private final CredentialVaultService vaultService;

    public VaultCredentialResolver(CredentialVaultService vaultService) {
        this.vaultService = vaultService;
    }

    @Override
    public String resolvePlainSecret(Long tenantId, Long providerId, String storedCipherOrPlain) {
        return vaultService.decrypt(storedCipherOrPlain);
    }

    @Override
    public String sealSecret(String rawSecret) {
        return vaultService.encrypt(rawSecret);
    }

    @Override
    public String rotateSecret(String storedSecret) {
        return vaultService.rotate(storedSecret);
    }

    @Override
    public String maskSecret(String storedSecret) {
        return vaultService.mask(storedSecret);
    }

    @Override
    public String fingerprint(String ciphertext) {
        return vaultService.fingerprint(ciphertext);
    }

    @Override
    public boolean isSealed(String storedValue) {
        return vaultService.isEncrypted(storedValue);
    }

    @Override
    public String activeKeyVersion() {
        return vaultService.activeKeyVersion();
    }
}
