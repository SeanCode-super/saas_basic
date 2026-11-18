package com.saasbasics.platform.modules.audit.dto;

public record HighRiskActionItemResponse(
        String code,
        String name,
        String httpMethod,
        String urlPattern,
        String category,
        String riskLevel,
        boolean requireApproval,
        String maskingPolicy,
        int retentionDays
) {
}
