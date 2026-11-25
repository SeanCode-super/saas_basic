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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Native Azure OpenAI Service adapter (api-key header + /openai/deployments/{deployment}/chat/completions).
 */
@Component
public class AzureOpenAiChatProvider implements ChatModelProvider {

    private static final String DEFAULT_API_VERSION = "2024-10-21";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Environment environment;

    public AzureOpenAiChatProvider(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public AzureOpenAiChatProvider(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = ProviderHttpSupport.defaultClient();
    }

    @Override
    public boolean supports(String providerType) {
        return "AZURE_OPENAI".equalsIgnoreCase(providerType);
    }

    @Override
    public ChatProviderResult chat(ChatProviderCommand command) {
        long start = System.currentTimeMillis();
        try {
            URI endpointUri = ProviderHttpSupport.validateEndpoint(
                    buildDeploymentUrl(command.endpointUrl(), command.modelId()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, false));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 30;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpointUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(timeoutSec));
            if (command.rawApiKey() != null && !command.rawApiKey().isBlank()) {
                builder.header("api-key", command.rawApiKey().trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String reply = root.at("/choices/0/message/content").asText("");
                int pTokens = root.at("/usage/prompt_tokens").asInt(0);
                int cTokens = root.at("/usage/completion_tokens").asInt(0);
                String cid = root.at("/id").asText("azcmpl-" + UUID.randomUUID().toString().substring(0, 8));
                return new ChatProviderResult(cid, command.modelId(), reply, TokenUsage.of(pTokens, cTokens), latency);
            }
            throw new AiDomainException("PROVIDER_" + classifyStatus(response.statusCode()).name(),
                    "Azure OpenAI 响应 HTTP " + response.statusCode());
        } catch (HttpTimeoutException ex) {
            throw new AiDomainException("PROVIDER_TIMEOUT", "Azure OpenAI 请求超时: " + ex.getMessage());
        } catch (AiDomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiDomainException("PROVIDER_ERROR", "Azure OpenAI 调用异常: " + ex.getMessage());
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
                    buildDeploymentUrl(command.endpointUrl(), command.modelId()), environment);
            String payload = objectMapper.writeValueAsString(buildPayload(command, true));
            int timeoutSec = command.timeoutSeconds() > 0 ? command.timeoutSeconds() : 60;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(endpointUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(timeoutSec));
            if (command.rawApiKey() != null && !command.rawApiKey().isBlank()) {
                builder.header("api-key", command.rawApiKey().trim());
            }

            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                onError.accept(classifyStatus(response.statusCode()));
                return;
            }
            TokenUsage usage = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", usage));
                        onComplete.run();
                        return;
                    }
                    JsonNode node = objectMapper.readTree(data);
                    if (!node.path("usage").isMissingNode() && !node.path("usage").isNull()) {
                        usage = TokenUsage.of(node.at("/usage/prompt_tokens").asInt(0), node.at("/usage/completion_tokens").asInt(0));
                    }
                    String delta = node.at("/choices/0/delta/content").asText("");
                    if (!delta.isEmpty()) {
                        onDelta.accept(new ChatStreamDelta(command.modelId(), delta, false, null, usage));
                    }
                }
            }
            onDelta.accept(new ChatStreamDelta(command.modelId(), "", true, "stop", usage));
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

    public static String buildDeploymentUrl(String baseUrl, String deploymentId) {
        String clean = baseUrl == null ? "" : baseUrl.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.contains("/chat/completions")) {
            return clean.contains("api-version=") ? clean : (clean + "?api-version=" + DEFAULT_API_VERSION);
        }
        String dep = URLEncoder.encode(deploymentId != null ? deploymentId.trim() : "gpt-4o", StandardCharsets.UTF_8);
        return clean + "/openai/deployments/" + dep + "/chat/completions?api-version=" + DEFAULT_API_VERSION;
    }

    private Map<String, Object> buildPayload(ChatProviderCommand command, boolean stream) {
        Map<String, Object> req = new HashMap<>();
        List<Map<String, String>> msgList = (command.messages() == null ? List.<ChatMessageData>of() : command.messages())
                .stream()
                .map(m -> Map.of("role", m.role(), "content", m.content() != null ? m.content() : ""))
                .toList();
        req.put("messages", msgList);
        req.put("stream", stream);
        if (command.temperature() != null) req.put("temperature", command.temperature());
        if (command.maxTokens() != null) req.put("max_tokens", command.maxTokens());
        return req;
    }
}
