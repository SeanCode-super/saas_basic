package com.saasbasics.platform.modules.ai.application.dto;

public record AiPromptSaveRequest(
        String promptCode,
        String promptName,
        String category,
        String systemPrompt,
        String userTemplate,
        String variablesJson,
        String modelHint,
        String changelog,
        Boolean publishImmediately
) {
}
