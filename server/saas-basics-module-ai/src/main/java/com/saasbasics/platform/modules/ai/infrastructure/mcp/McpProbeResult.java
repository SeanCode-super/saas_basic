package com.saasbasics.platform.modules.ai.infrastructure.mcp;

import java.util.List;

public record McpProbeResult(
        boolean success,
        String protocolVersion,
        String serverTitle,
        long latencyMs,
        String message,
        List<McpDiscoveredTool> discoveredTools
) {
}
