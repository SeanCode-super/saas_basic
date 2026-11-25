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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Native Anthropic Claude Messages API adapter (/v1/messages).
 */
@Component
public class AnthropicChatProvider implements ChatModelProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Environment environment;

    public AnthropicChatProvider(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public AnthropicChatProvider(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = ProviderHttpSupport.defaultClient();
    }

    @Override
    public boolean supports(String providerType) {
        return "ANTHROPIC".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatProviderResult chat(ChatProviderCommand command) {
        long start = System.currentTimeMillis();
        try {
            URI endpointUri = ProviderHttpSupport.validateEndpoint(buildMessagesUrl(command.endpointUrl()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, false));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 45;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                StringBuilder reply = new StringBuilder();
                if (root.path("content").isArray()) {
                    for (JsonNode block : root.path("content")) {
                        if ("text".equalsIgnoreCase(block.path("type").asText()) || block.has("text")) {
                            reply.append(block.path("text").asText(""));
                        }
                    }
                }
                int inTok = root.at("/usage/input_tokens").asInt(0);
                int outTok = root.at("/usage/output_tokens").asInt(0);
                String cid = root.path("id").asText("msg-" + UUID.randomUUID().toString().substring(0, 8));
                return new ChatProviderResult(cid, command.modelId(), reply.toString(), TokenUsage.of(inTok, outTok), latency);
            }
            throw new AiDomainException("PROVIDER_" + classifyStatus(response.statusCode()).name(),
                    "Anthropic 响应 HTTP " + response.statusCode());
        } catch (HttpTimeoutException ex) {
            throw new AiDomainException("PROVIDER_TIMEOUT", "Anthropic 请求超时: " + ex.getMessage());
        } catch (AiDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiDomainException("PROVIDER_ERROR", "Anthropic 调用异常: " + ex.getMessage());
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
            URI endpointUri = ProviderHttpSupport.validateEndpoint(buildMessagesUrl(command.endpointUrl()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, true));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 60;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                onError.accept(classifyStatus(response.statusCode()));
                return;
            }
            int inputTokens = 0;
            int outputTokens = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(data);
                    String type = node.path("type").asText("");
                    if ("message_start".equals(type)) {
                        inputTokens = node.at("/message/usage/input_tokens").asInt(inputTokens);
                    } else if ("content_block_delta".equals(type)) {
                        String text = node.at("/delta/text").asText("");
                        if (!text.isEmpty()) {
                            onDelta.accept(new ChatStreamDelta(command.modelId(), text, false, null, null));
                        }
                    } else if ("message_delta".equals(type)) {
                        outputTokens = node.at("/usage/output_tokens").asInt(outputTokens);
                    } else if ("message_stop".equals(type)) {
                        onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop",
                                TokenUsage.of(inputTokens, outputTokens)));
                        onComplete.run();
                        return;
                    }
                }
            }
            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop",
                    TokenUsage.of(inputTokens, outputTokens)));
            onComplete.run();
        } catch (HttpTimeoutException ex) {
            onError.accept(ProviderErrorCategory.TIMEOUT);
        } catch (Exception ex) {
            onError.accept(ProviderErrorCategory.PROVIDER_ERROR);
        }
    }

    @Override
    public ProviderErrorCategory classifyStatus(int httpStatusCode) {
        return ProviderHttpSupport.classifyStatus(httpStatusCode);
    }

    public static String buildMessagesUrl(String baseUrl) {
        String clean = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com/v1" : baseUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/messages")) {
            return clean;
        }
        if (clean.endsWith("/v1")) {
            return clean + "/messages";
        }
        return clean + "/v1/messages";
    }

    private HttpRequest buildHttpRequest(URI uri, String apiKey, String payload, int timeoutSec) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(timeoutSec));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey.trim());
        }
        return builder.build();
    }

    private Map<String, Object> buildPayload(ChatProviderCommand command, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", command.modelId());
        body.put("max_tokens", command.maxTokens() != null && command.maxTokens() > 0 ? command.maxTokens() : 4096);
        body.put("stream", stream);
        if (command.temperature() != null) {
            body.put("temperature", command.temperature());
        }
        StringBuilder systemBuilder = new StringBuilder();
        List<Map<String, String>> messages = new ArrayList<>();
        if (command.messages() != null) {
            for (ChatMessageData m : command.messages()) {
                if ("system".equalsIgnoreCase(m.role())) {
                    if (!systemBuilder.isEmpty()) {
                        systemBuilder.append("\n\n");
                    }
                    systemBuilder.append(m.content());
                } else {
                    String role = "assistant".equalsIgnoreCase(m.role()) ? "assistant" : "user";
                    messages.add(Map.of("role", role, "content", m.content() != null ? m.content() : ""));
                }
            }
        }
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "user", "content", "Hello"));
        }
        if (!systemBuilder.isEmpty()) {
            body.put("system", systemBuilder.toString());
        }
        body.put("messages", messages);
        return body;
    }
}
