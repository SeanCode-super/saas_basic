package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.service.McpServerService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Streamable HTTP 服务端端点，对外暴露已授权的平台 Tool 能力。
 */
@RestController
@RequestMapping("/api/ai/mcp/server")
@RequirePermission("system:config:query")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @PostMapping
    public ResponseEntity<?> handleMessage(
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId,
            @RequestBody Map<String, Object> request
    ) {
        String activeSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId.trim()
                : UUID.randomUUID().toString();
        Map<String, Object> response = mcpServerService.handleJsonRpc(request);
        if (response == null) {
            return ResponseEntity.accepted()
                    .header("Mcp-Session-Id", activeSessionId)
                    .build();
        }
        return ResponseEntity.ok()
                .header("Mcp-Session-Id", activeSessionId)
                .body(response);
    }
}
