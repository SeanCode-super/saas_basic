package com.saasbasics.platform.modules.ai.application.dto;

import com.saasbasics.platform.modules.ai.infrastructure.mcp.McpDiscoveredTool;
import java.util.List;

public record AiMcpProbeResultDTO(
        boolean success,
        String protocolVersion,
        String serverTitle,
        long latencyMs,
        String message,
        List<McpDiscoveredTool> discoveredTools
) {
}
