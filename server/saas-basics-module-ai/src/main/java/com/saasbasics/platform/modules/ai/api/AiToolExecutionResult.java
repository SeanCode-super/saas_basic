package com.saasbasics.platform.modules.ai.api;

public record AiToolExecutionResult(
        boolean success,
        String summary,
        String payloadJson
) {
    public static AiToolExecutionResult ok(String summary, String payloadJson) {
        return new AiToolExecutionResult(true, summary, payloadJson);
    }

    public static AiToolExecutionResult error(String summary) {
        return new AiToolExecutionResult(false, summary, "{}");
    }
}
