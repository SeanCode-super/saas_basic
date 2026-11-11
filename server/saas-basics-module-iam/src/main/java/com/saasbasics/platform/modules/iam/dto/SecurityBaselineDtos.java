package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class SecurityBaselineDtos {

    private SecurityBaselineDtos() {
    }

    public record SecurityBaselineResponse(
            int minLength,
            int maxLength,
            boolean requireUppercase,
            boolean requireLowercase,
            boolean requireNumber,
            boolean requireSpecial,
            int passwordHistory,
            int sessionTimeoutMinutes,
            boolean forceMfa,
            int maxFailedCount,
            int lockMinutes
    ) {
    }

    public record SecurityBaselineSaveRequest(
            @Min(6) @Max(32) int minLength,
            @Min(12) @Max(64) Integer maxLength,
            Boolean requireUppercase,
            Boolean requireLowercase,
            Boolean requireNumber,
            boolean requireSpecial,
            @Min(1) @Max(24) int passwordHistory,
            @Min(5) @Max(1440) int sessionTimeoutMinutes,
            boolean forceMfa,
            @Min(3) @Max(20) Integer maxFailedCount,
            @Min(5) @Max(1440) Integer lockMinutes
    ) {
    }
}
