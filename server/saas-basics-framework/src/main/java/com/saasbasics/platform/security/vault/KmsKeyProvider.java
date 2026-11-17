package com.saasbasics.platform.security.vault;

import java.util.Optional;

/**
 * 外部 KMS 主密钥提供者 SPI（可对接阿里云 KMS、AWS KMS 或 HashiCorp Vault）。
 */
public interface KmsKeyProvider {

    Optional<String> resolveMasterKey(String keyVersion);
}
