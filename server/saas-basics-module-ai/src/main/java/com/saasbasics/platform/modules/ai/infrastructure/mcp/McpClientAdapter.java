package com.saasbasics.platform.modules.ai.infrastructure.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.domain.CredentialResolver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * MCP (Model Context Protocol) JSON-RPC 2.0 协议适配器：
 * <ul>
 *   <li>支持 BUILTIN_LOOPBACK（内建本地闭环服务端）与 STREAMABLE_HTTP（外部 HTTP MCP 端点）</li>
 *   <li>兼容 MCP 2025-03-26 / 2024-11-05 协议握手机制与 SSE 响应流</li>
 *   <li>严格执行“默认拒绝全部工具，只允许显式白名单”安全策略</li>
 * </ul>
 */
@Component
public class McpClientAdapter {

    private static final String PROTOCOL_VERSION = "2025-03-26";

    private final ObjectMapper objectMapper;
    private final CredentialResolver credentialResolver;
    private final List<AiToolHandler> internalTools;
    private final HttpClient httpClient;
    private final Environment environment;

    public McpClientAdapter(
            ObjectMapper objectMapper,
            CredentialResolver credentialResolver,
            List<AiToolHandler> internalTools
    ) {
        this(objectMapper, credentialResolver, internalTools, null);
    }

    @Autowired
    public McpClientAdapter(
            ObjectMapper objectMapper,
            CredentialResolver credentialResolver,
            List<AiToolHandler> internalTools,
            Environment environment
    ) {
        this.objectMapper = objectMapper;
        this.credentialResolver = credentialResolver;
        this.internalTools = internalTools != null ? internalTools : List.of();
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public McpProbeResult probeAndDiscover(
            String transportType,
            String endpointUrl,
            String authHeaderName,
            String encryptedAuthToken,
            Set<String> whitelist
    ) {
        long start = System.currentTimeMillis();
        Set<String> safeWhitelist = whitelist != null ? whitelist : Set.of();

        if ("BUILTIN_LOOPBACK".equalsIgnoreCase(transportType)) {
            List<McpDiscoveredTool> tools = new ArrayList<>();
            for (AiToolHandler h : internalTools) {
                tools.add(new McpDiscoveredTool(
                        h.toolCode(),
                        h.description(),
                        h.inputSchemaJson(),
                        safeWhitelist.contains(h.toolCode())
                ));
            }
            long latency = System.currentTimeMillis() - start;
            return new McpProbeResult(
                    true,
                    PROTOCOL_VERSION,
                    "Tianshu Built-in Loopback MCP",
                    latency,
                    "MCP JSON-RPC 2.0 握手成功，发现 " + tools.size() + " 个工具（白名单放行 "
                            + tools.stream().filter(McpDiscoveredTool::whitelisted).count() + " 个）",
                    tools
            );
        }

        if (endpointUrl == null || endpointUrl.isBlank()) {
            return new McpProbeResult(false, PROTOCOL_VERSION, "", 0, "MCP 端点 URL 不能为空", List.of());
        }

        try {
            String rawToken = credentialResolver.resolvePlainSecret(null, null, encryptedAuthToken);

            // Step 1: JSON-RPC initialize
            JsonRpcCallResult initResult = sendJsonRpcWithSession(endpointUrl, authHeaderName, rawToken, null, "initialize", Map.of(
                    "protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "tianshu-saas-basics", "version", "1.0.0")
            ), true);
            JsonNode initResp = initResult.body();
            String sessionId = initResult.sessionId();

            // Best-effort MCP notifications/initialized handshake notification
            try {
                sendJsonRpcWithSession(endpointUrl, authHeaderName, rawToken, sessionId, "notifications/initialized", Map.of(), false);
            } catch (Exception ignored) {
            }

            String serverTitle = initResp.at("/result/serverInfo/name").asText("External MCP Server");
            String version = initResp.at("/result/protocolVersion").asText(PROTOCOL_VERSION);

            // Step 2: JSON-RPC tools/list
            JsonNode listResp = sendJsonRpcWithSession(endpointUrl, authHeaderName, rawToken, sessionId, "tools/list", Map.of(), true).body();
            List<McpDiscoveredTool> discovered = new ArrayList<>();
            JsonNode toolsArray = listResp.at("/result/tools");
            if (toolsArray.isArray()) {
                for (JsonNode item : toolsArray) {
                    String name = item.path("name").asText();
                    String desc = item.path("description").asText("");
                    String schema = item.has("inputSchema") ? objectMapper.writeValueAsString(item.get("inputSchema")) : "{}";
                    discovered.add(new McpDiscoveredTool(name, desc, schema, safeWhitelist.contains(name)));
                }
            }

            long latency = System.currentTimeMillis() - start;
            return new McpProbeResult(
                    true,
                    version,
                    serverTitle,
                    latency,
                    "MCP 外部端点握手成功 (" + version + ")，发现 " + discovered.size() + " 个声明工具",
                    discovered
            );
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            return new McpProbeResult(false, PROTOCOL_VERSION, "", latency, "MCP 探测失败: " + ex.getMessage(), List.of());
        }
    }

    public AiToolExecutionResult invokeTool(
            String transportType,
            String endpointUrl,
            String authHeaderName,
            String encryptedAuthToken,
            Set<String> whitelist,
            String toolName,
            Long tenantId,
            Long userId,
            Map<String, Object> arguments
    ) {
        if (whitelist == null || !whitelist.contains(toolName)) {
            return AiToolExecutionResult.error("安全策略拦截：MCP 工具 [" + toolName + "] 未在显式白名单中（默认拒绝）");
        }

        if ("BUILTIN_LOOPBACK".equalsIgnoreCase(transportType)) {
            return internalTools.stream()
                    .filter(t -> t.toolCode().equals(toolName))
                    .findFirst()
                    .map(handler -> handler.execute(tenantId, userId, arguments != null ? arguments : Map.of()))
                    .orElseGet(() -> AiToolExecutionResult.error("未找到对应的内建 MCP 工具实现: " + toolName));
        }

        try {
            String rawToken = credentialResolver.resolvePlainSecret(tenantId, null, encryptedAuthToken);
            JsonNode callResp = sendJsonRpcWithSession(endpointUrl, authHeaderName, rawToken, null, "tools/call", Map.of(
                    "name", toolName,
                    "arguments", arguments != null ? arguments : Map.of()
            ), true).body();
            if (callResp.has("error")) {
                return AiToolExecutionResult.error("MCP 远端调用报错: " + callResp.get("error").path("message").asText());
            }
            JsonNode resultNode = callResp.path("result");
            return AiToolExecutionResult.ok("MCP 工具调用成功", objectMapper.writeValueAsString(resultNode));
        } catch (Exception ex) {
            return AiToolExecutionResult.error("MCP 远端传输异常: " + ex.getMessage());
        }
    }

    public Set<String> parseWhitelist(String allowedToolsJson) {
        if (allowedToolsJson == null || allowedToolsJson.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(allowedToolsJson, new TypeReference<>() {});
            return Set.copyOf(list);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private record JsonRpcCallResult(JsonNode body, String sessionId) {}

    private JsonRpcCallResult sendJsonRpcWithSession(
            String endpointUrl,
            String authHeaderName,
            String rawToken,
            String mcpSessionId,
            String method,
            Map<String, Object> params,
            boolean includeId
    ) throws Exception {
        boolean allowPrivate = environment == null || !environment.matchesProfiles("prod");
        URI validatedUri = SsrfProtectionValidator.validateOutboundUri(endpointUrl, allowPrivate);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        if (includeId) {
            body.put("id", System.nanoTime());
        }
        body.put("method", method);
        body.put("params", params);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(validatedUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

        if (mcpSessionId != null && !mcpSessionId.isBlank()) {
            reqBuilder.header("Mcp-Session-Id", mcpSessionId);
        }

        if (rawToken != null && !rawToken.isBlank()) {
            String headerName = (authHeaderName == null || authHeaderName.isBlank()) ? "Authorization" : authHeaderName.trim();
            String value = "Authorization".equalsIgnoreCase(headerName) && !rawToken.startsWith("Bearer ")
                    ? "Bearer " + rawToken
                    : rawToken;
            reqBuilder.header(headerName, value);
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        String respSessionId = response.headers().firstValue("Mcp-Session-Id").orElse(mcpSessionId);
        String rawBody = response.body() != null ? response.body().trim() : "";
        if (rawBody.isEmpty()) {
            return new JsonRpcCallResult(objectMapper.createObjectNode(), respSessionId);
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("text/event-stream") || rawBody.startsWith("event:") || rawBody.startsWith("data:")) {
            for (String line : rawBody.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String jsonPart = trimmed.substring(5).trim();
                    if (!jsonPart.isEmpty() && !"[DONE]".equals(jsonPart)) {
                        return new JsonRpcCallResult(objectMapper.readTree(jsonPart), respSessionId);
                    }
                }
            }
        }
        return new JsonRpcCallResult(objectMapper.readTree(rawBody), respSessionId);
    }
}
