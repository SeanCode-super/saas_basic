package com.saasbasics.platform.modules.audit.dto;

public record RiskEvaluateResponse(
        String evaluatedAt,
        String matchedRule,
        String decision,
        String matchedThreshold,
        String enforcedAction,
        String latencyMs,
        long recentLoginFailures,
        long recentOperations
) {
}
