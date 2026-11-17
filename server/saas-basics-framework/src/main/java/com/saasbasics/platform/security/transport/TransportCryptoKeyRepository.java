package com.saasbasics.platform.security.transport;

import java.time.Instant;
import java.util.List;

/**
 * SPI 接口：负责持久化 X25519 传输加密密钥环，避免 framework 层反向依赖业务持久化模块。
 */
public interface TransportCryptoKeyRepository {

    record PersistedKeyRecord(
            String keyId,
            String keySource,
            String sealedPrivateKey,
            String publicKeyBase64,
            String fingerprintSha256,
            boolean active,
            Instant issuedAt,
            String remark
    ) {
    }

    List<PersistedKeyRecord> loadRecentKeys(int maxCount);

    void saveActiveKey(PersistedKeyRecord record);
}
