package com.saasbasics.platform.modules.ai.application.dto;

import java.util.Map;

public record AiRunCreateRequest(
        String modelId,
        String promptCode,
        String inputPrompt,
        String toolCode,
        Map<String, Object> toolArguments
) {
}
