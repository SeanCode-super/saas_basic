package com.saasbasics.platform.security.transport;

import java.time.Instant;
import java.util.List;

/**
 * 不可变传输加密运行期策略快照（用于原子切换、前端协商与持久化同步）。
 */
public record TransportCryptoPolicySnapshot(
        TransportCryptoMode mode,
        TransportScopeMode scopeMode,
        String algorithm,
        boolean encryptRequest,
        boolean encryptResponse,
        boolean encryptQuery,
        boolean replayProtection,
        int timestampSkewSeconds,
        int keyRotationHours,
        int maxGraceKeys,
        List<String> includePaths,
        List<String> excludePaths,
        List<String> sensitivePaths,
        long policyVersion,
        Instant updatedAt,
        String remark
) {
    public static final String ALGORITHM_SUITE = "X25519-HKDF-AES-256-GCM";

    public TransportCryptoPolicySnapshot(
            TransportCryptoMode mode,
            TransportScopeMode scopeMode,
            String algorithm,
            boolean encryptRequest,
            boolean encryptResponse,
            boolean encryptQuery,
            boolean replayProtection,
            int timestampSkewSeconds,
            List<String> includePaths,
            List<String> excludePaths,
            List<String> sensitivePaths,
            long policyVersion,
            Instant updatedAt,
            String remark) {
        this(
                mode,
                scopeMode,
                algorithm,
                encryptRequest,
                encryptResponse,
                encryptQuery,
                replayProtection,
                timestampSkewSeconds,
                24,
                3,
                includePaths,
                excludePaths,
                sensitivePaths,
                policyVersion,
                updatedAt,
                remark
        );
    }
}
