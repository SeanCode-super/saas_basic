package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public record AiPromptVersionDTO(
        Long id,
        Long promptId,
        Integer versionNo,
        String systemPrompt,
        String userTemplate,
        String variablesJson,
        String modelHint,
        String changelog,
        Integer isPublished,
        LocalDateTime createdAt
) {
}
