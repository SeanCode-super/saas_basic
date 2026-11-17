package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public record AiToolCallDTO(
        Long id,
        Long runId,
        String callNo,
        String toolCode,
        String adapterType,
        String riskLevel,
        String permissionCode,
        String idempotencyKey,
        String argumentsSummary,
        String resultSummary,
        boolean permissionGranted,
        String approvalStatus,
        Long approvedBy,
        LocalDateTime approvedAt,
        String rejectReason,
        String executionStatus,
        Long latencyMs,
        LocalDateTime createdAt
) {
}
