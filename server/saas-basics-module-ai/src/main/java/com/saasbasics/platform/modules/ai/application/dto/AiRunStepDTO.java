package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public record AiRunStepDTO(
        Long id,
        Integer stepNo,
        String stepType,
        String modelId,
        String toolCode,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        String errorType,
        String detailJson,
        LocalDateTime createdAt
) {
}
