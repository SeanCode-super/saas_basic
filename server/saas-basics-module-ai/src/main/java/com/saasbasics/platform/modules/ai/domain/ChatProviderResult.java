package com.saasbasics.platform.modules.ai.domain;

public record ChatProviderResult(
        String responseId,
        String modelId,
        String content,
        TokenUsage usage,
        long latencyMs
) {
}
