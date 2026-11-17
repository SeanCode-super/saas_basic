package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public record AiPromptDTO(
        Long id,
        Long tenantId,
        String promptCode,
        String promptName,
        String category,
        Integer activeVersion,
        String status,
        String systemPrompt,
        String userTemplate,
        String modelHint,
        String remark,
        LocalDateTime updatedAt
) {
}
