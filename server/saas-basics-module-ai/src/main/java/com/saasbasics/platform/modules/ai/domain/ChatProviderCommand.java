package com.saasbasics.platform.modules.ai.domain;

import java.util.List;

public record ChatProviderCommand(
        String providerType,
        String providerCode,
        String endpointUrl,
        String rawApiKey,
        String modelId,
        List<ChatMessageData> messages,
        Double temperature,
        Integer maxTokens,
        int timeoutSeconds
) {
}
