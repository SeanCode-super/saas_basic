package com.saasbasics.platform.modules.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McpServerServiceTest {

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleMcpHandshakePermissionFilteredListAndToolCall() {
        AiToolHandler echoTool = new AiToolHandler() {
            @Override
            public String toolCode() {
                return "platform.echo";
            }

            @Override
            public String toolName() {
                return "Platform Echo";
            }

            @Override
            public String description() {
                return "Echo arguments";
            }

            @Override
            public String permissionCode() {
                return "system:config:query";
            }

            @Override
            public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
                return AiToolExecutionResult.ok("Echoed for tenant " + tenantId, "{\"ok\":true}");
            }
        };

        AiToolHandler restrictedTool = new AiToolHandler() {
            @Override
            public String toolCode() {
                return "platform.secret_reset";
            }

            @Override
            public String toolName() {
                return "Secret Reset";
            }

            @Override
            public String description() {
                return "Admin only";
            }

            @Override
            public String permissionCode() {
                return "iam:role:write";
            }

            @Override
            public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
                return AiToolExecutionResult.ok("Done", "{}");
            }
        };

        McpServerService service = new McpServerService(List.of(echoTool, restrictedTool), new ObjectMapper());

        Map<String, Object> initResp = service.handleJsonRpc(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize"));
        Map<String, Object> initResult = (Map<String, Object>) initResp.get("result");
        assertThat(initResult.get("protocolVersion")).isEqualTo(McpServerService.PROTOCOL_VERSION);

        AuthContext.set(new AuthPrincipal(
                10L, 3L, "demo", 20L, "user", "User", "TENANT_USER", "ONLINE",
                null, List.of("system:config:query"), List.of(), List.of(), List.of()
        ));

        Map<String, Object> listResp = service.handleJsonRpc(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"));
        List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) listResp.get("result")).get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name")).isEqualTo("platform.echo");

        Map<String, Object> callResp = service.handleJsonRpc(Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of("name", "platform.echo", "arguments", Map.of("k", "v"))
        ));
        Map<String, Object> callResult = (Map<String, Object>) callResp.get("result");
        assertThat(callResult.get("isError")).isEqualTo(false);
    }
}
