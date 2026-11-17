package com.saasbasics.platform.modules.ai.domain;

/**
 * 领域层凭证解析 SPI：按租户安全解析短期可用凭据，禁止向上层 API 返回明文。
 */
public interface CredentialResolver {

    String resolvePlainSecret(Long tenantId, Long providerId, String storedCipherOrPlain);

    String sealSecret(String rawSecret);

    String rotateSecret(String storedSecret);

    String maskSecret(String storedSecret);

    String fingerprint(String ciphertext);

    boolean isSealed(String storedValue);

    String activeKeyVersion();
}
