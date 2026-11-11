package com.saasbasics.platform.modules.ai.domain;

public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public static TokenUsage of(int promptTokens, int completionTokens) {
        int p = Math.max(0, promptTokens);
        int c = Math.max(0, completionTokens);
        return new TokenUsage(p, c, p + c);
    }
}
