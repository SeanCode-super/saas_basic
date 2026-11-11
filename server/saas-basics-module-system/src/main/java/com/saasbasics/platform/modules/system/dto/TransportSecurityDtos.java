package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class TransportSecurityDtos {

    private TransportSecurityDtos() {
    }

    public record RetainedKeySummary(
            String keyId,
            String keySource,
            String publicKey,
            String fingerprintSha256,
            long issuedAtEpochMs,
            boolean active,
            String remark
    ) {
    }

    public record TransportKeySummary(
            String activeKid,
            String keySource,
            String publicKey,
            String fingerprintSha256,
            long issuedAtEpochMs,
            int retainedKeyCount,
            int keyRotationHours,
            int maxGraceKeys,
            boolean vaultUsingDefaultKey,
            String vaultActiveKeyVersion,
            List<RetainedKeySummary> retainedKeys
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
            int keyRotationHours,
            int maxGraceKeys,
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
            @Min(1) @Max(720) Integer keyRotationHours,
            @Min(1) @Max(10) Integer maxGraceKeys,
            List<String> includePaths,
            List<String> excludePaths,
            List<String> sensitivePaths,
            String remark
    ) {
    }

    public record TransportKeyConfigureRequest(
            @NotBlank String mode,
            String keyId,
            String privateKeyInput,
            @Min(1) @Max(720) Integer keyRotationHours,
            @Min(1) @Max(10) Integer maxGraceKeys,
            String remark
    ) {
    }

    public record TransportKeyActivateRequest(
            @NotBlank String keyId
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
