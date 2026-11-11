package com.saasbasics.platform.security.transport;

import com.saasbasics.platform.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开传输加密策略与协商公钥下发端点（登录前可用，仅下发非敏感策略与活跃 X25519 公钥）。
 */
@RestController
@RequestMapping("/api/security/transport")
public class TransportCryptoPublicController {

    private final TransportCryptoPolicyService policyService;
    private final TransportCryptoKeyRing keyRing;

    public TransportCryptoPublicController(TransportCryptoPolicyService policyService,
                                           TransportCryptoKeyRing keyRing) {
        this.policyService = policyService;
        this.keyRing = keyRing;
    }

    public record PublicTransportPolicyResponse(
            String mode,
            String scopeMode,
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
            String activeKid,
            String publicKey,
            String fingerprintSha256,
            long keyIssuedAtEpochMs,
            long serverTimestampMs
    ) {
    }

    @GetMapping("/policy")
    @TransportCrypto(TransportCrypto.Rule.PLAIN)
    public ApiResponse<PublicTransportPolicyResponse> publicPolicy() {
        TransportCryptoPolicySnapshot policy = policyService.getCurrentPolicy();
        TransportCryptoKeyRing.ServerKeyEntry key = keyRing.getActiveKey();
        return ApiResponse.success(new PublicTransportPolicyResponse(
                policy.mode().name(),
                policy.scopeMode().name(),
                policy.algorithm(),
                policy.encryptRequest(),
                policy.encryptResponse(),
                policy.encryptQuery(),
                policy.replayProtection(),
                policy.timestampSkewSeconds(),
                policy.includePaths(),
                policy.excludePaths(),
                policy.sensitivePaths(),
                policy.policyVersion(),
                key.keyId(),
                key.publicKeyBase64(),
                key.fingerprintSha256(),
                key.issuedAt().toEpochMilli(),
                System.currentTimeMillis()
        ));
    }
}
