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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Native Ollama REST API adapter (/api/chat, supports NDJSON streaming without mandatory API key).
 */
@Component
public class OllamaNativeChatProvider implements ChatModelProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Environment environment;

    public OllamaNativeChatProvider(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public OllamaNativeChatProvider(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = ProviderHttpSupport.defaultClient();
    }

    @Override
    public boolean supports(String providerType) {
        return "OLLAMA_NATIVE".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatProviderResult chat(ChatProviderCommand command) {
        long start = System.currentTimeMillis();
        try {
            URI endpointUri = ProviderHttpSupport.validateEndpoint(buildChatUrl(command.endpointUrl()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, false));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 60;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String reply = root.at("/message/content").asText("");
                int pTok = root.path("prompt_eval_count").asInt(0);
                int cTok = root.path("eval_count").asInt(0);
                String cid = "ollama-" + UUID.randomUUID().toString().substring(0, 8);
                return new ChatProviderResult(cid, command.modelId(), reply, TokenUsage.of(pTok, cTok), latency);
            }
            throw new AiDomainException("PROVIDER_" + classifyStatus(response.statusCode()).name(),
                    "Ollama 响应 HTTP " + response.statusCode());
        } catch (HttpTimeoutException ex) {
            throw new AiDomainException("PROVIDER_TIMEOUT", "Ollama 请求超时: " + ex.getMessage());
        } catch (AiDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiDomainException("PROVIDER_ERROR", "Ollama 调用异常: " + ex.getMessage());
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
            URI endpointUri = ProviderHttpSupport.validateEndpoint(buildChatUrl(command.endpointUrl()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, true));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 90;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                onError.accept(classifyStatus(response.statusCode()));
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode node = objectMapper.readTree(line);
                    String chunk = node.at("/message/content").asText("");
                    if (!chunk.isEmpty()) {
                        onDelta.accept(new ChatStreamDelta(command.modelId(), chunk, false, null, null));
                    }
                    if (node.path("done").asBoolean(false)) {
                        TokenUsage usage = TokenUsage.of(
                                node.path("prompt_eval_count").asInt(0),
                                node.path("eval_count").asInt(0));
                        onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", usage));
                        onComplete.run();
                        return;
                    }
                }
            }
            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", null));
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

    public static String buildChatUrl(String baseUrl) {
        String clean = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:11434" : baseUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/api/chat")) {
            return clean;
        }
        if (clean.endsWith("/v1")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean + "/api/chat";
    }

    private HttpRequest buildHttpRequest(URI uri, String apiKey, String payload, int timeoutSec) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(timeoutSec));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        return builder.build();
    }

    private Map<String, Object> buildPayload(ChatProviderCommand command, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", command.modelId());
        body.put("stream", stream);
        List<Map<String, String>> msgList = (command.messages() == null ? List.<ChatMessageData>of() : command.messages())
                .stream()
                .map(m -> Map.of("role", m.role(), "content", m.content() != null ? m.content() : ""))
                .toList();
        body.put("messages", msgList);
        Map<String, Object> options = new LinkedHashMap<>();
        if (command.temperature() != null) options.put("temperature", command.temperature());
        if (command.maxTokens() != null) options.put("num_predict", command.maxTokens());
        if (!options.isEmpty()) body.put("options", options);
        return body;
    }
}
