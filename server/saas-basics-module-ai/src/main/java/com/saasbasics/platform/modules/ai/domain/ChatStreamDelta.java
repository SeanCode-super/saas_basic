package com.saasbasics.platform.modules.ai.domain;

public record ChatStreamDelta(
        String modelId,
        String contentDelta,
        boolean finished,
        String finishReason,
        TokenUsage finalUsage
) {
}
