package com.saasbasics.platform.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.ai.domain.AiDomainException;
import com.saasbasics.platform.modules.ai.domain.ChatMessageData;
import com.saasbasics.platform.modules.ai.domain.ChatModelProvider;
import com.saasbasics.platform.modules.ai.domain.ChatProviderCommand;
import com.saasbasics.platform.modules.ai.domain.ChatProviderResult;
import com.saasbasics.platform.modules.ai.domain.ChatStreamDelta;
import com.saasbasics.platform.modules.ai.domain.ProviderErrorCategory;
import com.saasbasics.platform.modules.ai.domain.TokenUsage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleChatProvider implements ChatModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatProvider.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean supports(String providerType) {
        return providerType == null
                || "OPENAI_COMPATIBLE".equalsIgnoreCase(providerType)
                || "OLLAMA".equalsIgnoreCase(providerType)
                || "CUSTOM".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatProviderResult chat(ChatProviderCommand command) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> reqBody = buildPayload(command, false);
            String jsonPayload = objectMapper.writeValueAsString(reqBody);
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 30;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(command.endpointUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(timeoutSec));

            if (command.rawApiKey() != null && !command.rawApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + command.rawApiKey().trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String reply = root.at("/choices/0/message/content").asText("");
                int pTokens = root.at("/usage/prompt_tokens").asInt(0);
                int cTokens = root.at("/usage/completion_tokens").asInt(0);
                String cid = root.at("/id").asText("chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
                return new ChatProviderResult(cid, command.modelId(), reply, TokenUsage.of(pTokens, cTokens), latency);
            }

            ProviderErrorCategory category = classifyStatus(response.statusCode());
            throw new AiDomainException("PROVIDER_" + category.name(), "上游模型响应 HTTP " + response.statusCode());
        } catch (HttpTimeoutException ex) {
            throw new AiDomainException("PROVIDER_TIMEOUT", "模型请求超时: " + ex.getMessage());
        } catch (AiDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiDomainException("PROVIDER_ERROR", "模型调用异常: " + ex.getMessage());
        }
    }

    @Override
    public void streamChat(
            ChatProviderCommand command,
            Consumer<ChatStreamDelta> onDelta,
            Runnable onComplete,
            Consumer<ProviderErrorCategory> onError
    ) {
        try {
            Map<String, Object> reqBody = buildPayload(command, true);
            String jsonPayload = objectMapper.writeValueAsString(reqBody);
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 60;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(command.endpointUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(timeoutSec));

            if (command.rawApiKey() != null && !command.rawApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + command.rawApiKey().trim());
            }

            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("[OpenAiCompatibleChatProvider] Stream status HTTP {}", response.statusCode());
                onError.accept(classifyStatus(response.statusCode()));
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("data: ")) {
                        String dataStr = line.substring(6).trim();
                        if ("[DONE]".equals(dataStr)) {
                            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", null));
                            onComplete.run();
                            return;
                        }
                        try {
                            JsonNode chunkNode = objectMapper.readTree(dataStr);
                            JsonNode delta = chunkNode.at("/choices/0/delta/content");
                            JsonNode usageNode = chunkNode.at("/usage");
                            TokenUsage usage = null;
                            if (!usageNode.isMissingNode() && !usageNode.isNull()) {
                                usage = TokenUsage.of(
                                        usageNode.path("prompt_tokens").asInt(0),
                                        usageNode.path("completion_tokens").asInt(0)
                                );
                            }
                            if (!delta.isMissingNode()) {
                                onDelta.accept(new ChatStreamDelta(command.modelId(), delta.asText(""), false, null, usage));
                            } else if (usage != null) {
                                onDelta.accept(new ChatStreamDelta(command.modelId(), "", false, null, usage));
                            }
                        } catch (Exception ignored) {
                            // Non-JSON chunk heartbeat
                        }
                    }
                }
            }
            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", null));
            onComplete.run();
        } catch (HttpTimeoutException ex) {
            onError.accept(ProviderErrorCategory.TIMEOUT);
        } catch (Exception ex) {
            log.warn("[OpenAiCompatibleChatProvider] Stream error: {}", ex.getMessage());
            onError.accept(ProviderErrorCategory.PROVIDER_ERROR);
        }
    }

    @Override
    public ProviderErrorCategory classifyStatus(int status) {
        if (status == 401 || status == 403) {
            return ProviderErrorCategory.AUTH_FAILED;
        }
        if (status == 429) {
            return ProviderErrorCategory.RATE_LIMIT;
        }
        if (status == 408 || status == 504) {
            return ProviderErrorCategory.TIMEOUT;
        }
        if (status == 400 || status == 422) {
            return ProviderErrorCategory.NON_RETRYABLE;
        }
        if (status == 451) {
            return ProviderErrorCategory.CONTENT_SAFETY;
        }
        return ProviderErrorCategory.PROVIDER_ERROR;
    }

    private Map<String, Object> buildPayload(ChatProviderCommand command, boolean stream) {
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("model", command.modelId());
        List<Map<String, String>> msgs = command.messages().stream()
                .map(OpenAiCompatibleChatProvider::toMsgMap)
                .toList();
        reqBody.put("messages", msgs);
        reqBody.put("stream", stream);
        if (command.temperature() != null) {
            reqBody.put("temperature", command.temperature());
        }
        if (command.maxTokens() != null && command.maxTokens() > 0) {
            reqBody.put("max_tokens", command.maxTokens());
        }
        if (stream) {
            reqBody.put("stream_options", Map.of("include_usage", true));
        }
        return reqBody;
    }

    private static Map<String, String> toMsgMap(ChatMessageData m) {
        return Map.of("role", m.role(), "content", m.content() != null ? m.content() : "");
    }
}
