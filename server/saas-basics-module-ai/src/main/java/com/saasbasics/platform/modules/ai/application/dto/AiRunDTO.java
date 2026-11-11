package com.saasbasics.platform.modules.ai.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AiRunDTO(
        Long id,
        Long tenantId,
        String runNo,
        Long userId,
        String username,
        String modelId,
        String providerCode,
        String promptCode,
        Integer promptVersion,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal estimatedCost,
        Long latencyMs,
        String errorType,
        String errorMessage,
        String traceId,
        String inputSummary,
        String outputSummary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<AiRunStepDTO> steps,
        List<AiToolCallDTO> toolCalls
) {
}
