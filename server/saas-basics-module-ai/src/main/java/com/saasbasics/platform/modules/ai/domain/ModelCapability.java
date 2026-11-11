package com.saasbasics.platform.modules.ai.domain;

public record ModelCapability(
        String contextWindow,
        boolean supportsTools,
        boolean supportsJsonOutput,
        boolean supportsStreaming,
        boolean supportsVision
) {
}
