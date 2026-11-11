package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public record AiCredentialRefDTO(
        Long id,
        Long tenantId,
        String credentialCode,
        Long providerId,
        String keyAlias,
        String maskedHint,
        String encryptionAlgo,
        String keyVersion,
        String cipherDigest,
        Integer rotationCount,
        LocalDateTime lastRotatedAt,
        String status,
        String remark
) {
}
