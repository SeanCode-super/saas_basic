package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class TransportSecurityDtos {

    private TransportSecurityDtos() {
    }

    public record TransportKeySummary(
            String activeKid,
            String publicKey,
            String fingerprintSha256,
            long issuedAtEpochMs,
            int retainedKeyCount
    ) {
    }

    public record TransportTelemetrySummary(
            long totalRequests,
            long encryptedRequests,
            long plaintextAllowed,
            long rejectedPlaintext,
            long rejectedReplay,
            long decryptErrors
    ) {
    }

    public record TransportSecurityOverviewResponse(
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
            long updatedAtEpochMs,
            String remark,
            TransportKeySummary keySummary,
            TransportTelemetrySummary telemetry
    ) {
    }

    public record TransportPolicyUpdateRequest(
            @NotBlank String mode,
            @NotBlank String scopeMode,
            boolean encryptRequest,
            boolean encryptResponse,
            boolean encryptQuery,
            boolean replayProtection,
            @Min(30) @Max(3600) int timestampSkewSeconds,
            List<String> includePaths,
            List<String> excludePaths,
            List<String> sensitivePaths,
            String remark
    ) {
    }

    public record TransportRoundtripRequest(
            String probeText,
            Long clientSentAtMs
    ) {
    }

    public record TransportRoundtripResponse(
            String echoedText,
            boolean transportEncrypted,
            String mode,
            String scopeMode,
            String activeKid,
            long policyVersion,
            long serverHandledAtMs
    ) {
    }
}
