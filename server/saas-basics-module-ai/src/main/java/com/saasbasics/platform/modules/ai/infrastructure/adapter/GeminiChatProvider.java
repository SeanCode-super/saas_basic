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
import java.net.URLEncoder;
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
 * Native Google Gemini REST adapter (:generateContent / :streamGenerateContent?alt=sse).
 */
@Component
public class GeminiChatProvider implements ChatModelProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Environment environment;

    public GeminiChatProvider(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public GeminiChatProvider(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = ProviderHttpSupport.defaultClient();
    }

    @Override
    public boolean supports(String providerType) {
        return "GEMINI".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatProviderResult chat(ChatProviderCommand command) {
        long start = System.currentTimeMillis();
        try {
            URI endpointUri = ProviderHttpSupport.validateEndpoint(
                    buildGenerateUrl(command.endpointUrl(), command.modelId(), false), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 45;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String reply = extractText(root);
                int inTok = root.at("/usageMetadata/promptTokenCount").asInt(0);
                int outTok = root.at("/usageMetadata/candidatesTokenCount").asInt(0);
                String cid = "gemini-" + UUID.randomUUID().toString().substring(0, 8);
                return new ChatProviderResult(cid, command.modelId(), reply, TokenUsage.of(inTok, outTok), latency);
            }
            throw new AiDomainException("PROVIDER_" + classifyStatus(response.statusCode()).name(),
                    "Gemini 响应 HTTP " + response.statusCode());
        } catch (HttpTimeoutException ex) {
            throw new AiDomainException("PROVIDER_TIMEOUT", "Gemini 请求超时: " + ex.getMessage());
        } catch (AiDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiDomainException("PROVIDER_ERROR", "Gemini 调用异常: " + ex.getMessage());
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
            URI endpointUri = ProviderHttpSupport.validateEndpoint(
                    buildGenerateUrl(command.endpointUrl(), command.modelId(), true), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 60;

            HttpRequest request = buildHttpRequest(endpointUri, command.rawApiKey(), payload, timeoutSec);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                onError.accept(classifyStatus(response.statusCode()));
                return;
            }
            int pTok = 0;
            int cTok = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isBlank() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    String chunk = extractText(root);
                    if (!chunk.isEmpty()) {
                        onDelta.accept(new ChatStreamDelta(command.modelId(), chunk, false, null, null));
                    }
                    if (!root.path("usageMetadata").isMissingNode()) {
                        pTok = root.at("/usageMetadata/promptTokenCount").asInt(pTok);
                        cTok = root.at("/usageMetadata/candidatesTokenCount").asInt(cTok);
                    }
                }
            }
            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", TokenUsage.of(pTok, cTok)));
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

    public static String buildGenerateUrl(String baseUrl, String modelId, boolean stream) {
        String clean = (baseUrl == null || baseUrl.isBlank())
                ? "https://generativelanguage.googleapis.com/v1beta"
                : baseUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.contains(":generateContent") || clean.contains(":streamGenerateContent")) {
            return clean;
        }
        String model = modelId != null && !modelId.isBlank() ? modelId.trim() : "gemini-2.5-flash";
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20");
        String prefix = clean.endsWith("/models") ? clean : (clean + "/models");
        return stream
                ? prefix + "/" + encodedModel + ":streamGenerateContent?alt=sse"
                : prefix + "/" + encodedModel + ":generateContent";
    }

    private HttpRequest buildHttpRequest(URI uri, String apiKey, String payload, int timeoutSec) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(timeoutSec));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-goog-api-key", apiKey.trim());
        }
        return builder.build();
    }

    private Map<String, Object> buildPayload(ChatProviderCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        StringBuilder systemBuilder = new StringBuilder();
        List<Map<String, Object>> contents = new ArrayList<>();

        if (command.messages() != null) {
            for (ChatMessageData m : command.messages()) {
                if ("system".equalsIgnoreCase(m.role())) {
                    if (!systemBuilder.isEmpty()) {
                        systemBuilder.append("\n\n");
                    }
                    systemBuilder.append(m.content());
                } else {
                    String role = "assistant".equalsIgnoreCase(m.role()) ? "model" : "user";
                    contents.add(Map.of(
                            "role", role,
                            "parts", List.of(Map.of("text", m.content() != null ? m.content() : ""))
                    ));
                }
            }
        }
        if (contents.isEmpty()) {
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", "Hello"))));
        }
        if (!systemBuilder.isEmpty()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemBuilder.toString()))));
        }
        body.put("contents", contents);

        Map<String, Object> genConfig = new LinkedHashMap<>();
        if (command.temperature() != null) {
            genConfig.put("temperature", command.temperature());
        }
        if (command.maxTokens() != null && command.maxTokens() > 0) {
            genConfig.put("maxOutputTokens", command.maxTokens());
        }
        if (!genConfig.isEmpty()) {
            body.put("generationConfig", genConfig);
        }
        return body;
    }

    private String extractText(JsonNode root) {
        JsonNode parts = root.at("/candidates/0/content/parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.path("text").asText(""));
            }
        }
        return sb.toString();
    }
}
