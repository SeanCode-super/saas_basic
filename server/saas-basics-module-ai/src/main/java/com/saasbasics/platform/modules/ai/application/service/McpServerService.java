package com.saasbasics.platform.modules.ai.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 双向 MCP Streamable HTTP 服务端 (Model Context Protocol 2025-03-26)。
 * 允许 Claude Desktop / Cursor / 外部 Agent 在携带授权会话并受控鉴权后发现与调用天枢平台业务工具。
 */
@Service
public class McpServerService {

    public static final String PROTOCOL_VERSION = "2025-03-26";

    private final Map<String, AiToolHandler> toolHandlers;
    private final ObjectMapper objectMapper;

    public McpServerService(List<AiToolHandler> handlers, ObjectMapper objectMapper) {
        this.toolHandlers = handlers == null
                ? Map.of()
                : handlers.stream().collect(Collectors.toMap(
                        AiToolHandler::toolCode,
                        h -> h,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleJsonRpc(Map<String, Object> request) {
        if (request == null) {
            return jsonRpcError(null, -32600, "Invalid JSON-RPC request");
        }
        Object id = request.get("id");
        Object methodObj = request.get("method");
        String method = methodObj != null ? String.valueOf(methodObj).trim() : "";
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        return switch (method) {
            case "initialize" -> jsonRpcResult(id, Map.of(
                    "protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of("tools", Map.of("listChanged", false)),
                    "serverInfo", Map.of("name", "tianshu-saas-mcp-server", "version", "1.0.0")
            ));
            case "notifications/initialized" -> null;
            case "ping" -> jsonRpcResult(id, Map.of());
            case "tools/list" -> jsonRpcResult(id, handleToolsList(AuthContext.get()));
            case "tools/call" -> handleToolsCall(id, params, AuthContext.get());
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    private Map<String, Object> handleToolsList(AuthPrincipal principal) {
        List<Map<String, Object>> tools = toolHandlers.values().stream()
                .filter(handler -> isAuthorized(principal, handler.permissionCode()))
                .map(handler -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", handler.toolCode());
                    item.put("description", handler.description() != null ? handler.description() : handler.toolName());
                    item.put("inputSchema", parseInputSchema(handler.inputSchemaJson()));
                    return item;
                })
                .toList();
        return Map.of("tools", tools);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> params, AuthPrincipal principal) {
        Object nameObj = params.get("name");
        String toolName = nameObj != null ? String.valueOf(nameObj).trim() : "";
        if (toolName.isEmpty()) {
            return jsonRpcError(id, -32602, "Missing required param: name");
        }
        AiToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            return jsonRpcError(id, -32601, "Unknown MCP tool: " + toolName);
        }
        if (!isAuthorized(principal, handler.permissionCode())) {
            return jsonRpcError(id, -32003, "Forbidden: missing permission " + handler.permissionCode());
        }

        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
        Long userId = principal != null && principal.userId() != null ? principal.userId() : 0L;

        try {
            AiToolExecutionResult execResult = handler.execute(tenantId, userId, arguments);
            String textContent = execResult.payloadJson() != null && !execResult.payloadJson().isBlank()
                    ? execResult.summary() + "\n" + execResult.payloadJson()
                    : execResult.summary();
            return jsonRpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", textContent != null ? textContent : "")),
                    "isError", !execResult.success()
            ));
        } catch (Exception ex) {
            return jsonRpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Tool execution failed: " + ex.getMessage())),
                    "isError", true
            ));
        }
    }

    private boolean isAuthorized(AuthPrincipal principal, String permissionCode) {
        if (principal == null) {
            return false;
        }
        if (principal.isSuperAdmin()) {
            return true;
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            return true;
        }
        List<String> perms = principal.permissions();
        return perms != null && (perms.contains("*") || perms.contains(permissionCode));
    }

    private Map<String, Object> parseInputSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private Map<String, Object> jsonRpcResult(Object id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return body;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", Map.of("code", code, "message", message));
        return body;
    }
}
