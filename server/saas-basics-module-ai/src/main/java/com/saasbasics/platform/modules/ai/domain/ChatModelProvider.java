package com.saasbasics.platform.modules.ai.domain;

import java.util.function.Consumer;

/**
 * 领域层 Chat 模型供应商 SPI（严禁泄露任何厂商 SDK 类型）。
 */
public interface ChatModelProvider {

    boolean supports(String providerType);

    ChatProviderResult chat(ChatProviderCommand command);

    void streamChat(
            ChatProviderCommand command,
            Consumer<ChatStreamDelta> onDelta,
            Runnable onComplete,
            Consumer<ProviderErrorCategory> onError
    );

    ProviderErrorCategory classifyStatus(int httpStatusCode);
}
