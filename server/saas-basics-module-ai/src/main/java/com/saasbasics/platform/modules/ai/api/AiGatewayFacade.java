package com.saasbasics.platform.modules.ai.api;

import java.util.List;

/**
 * 跨模块统一 AI 门面接口，供业务或代码生成等模块受控调用并统一计量审计。
 */
public interface AiGatewayFacade {

    record ChatMessage(String role, String content) {}

    record ChatCompletionCommand(
            String modelId,
            String promptCode,
            List<ChatMessage> messages,
            Double temperature
    ) {}

    record ChatCompletionOutput(
            String runNo,
            String modelId,
            String reply,
            int promptTokens,
            int completionTokens,
            long latencyMs
    ) {}

    ChatCompletionOutput complete(ChatCompletionCommand command);
}
