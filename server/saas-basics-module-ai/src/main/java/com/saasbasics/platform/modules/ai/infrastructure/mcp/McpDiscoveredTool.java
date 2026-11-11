package com.saasbasics.platform.modules.ai.infrastructure.mcp;

public record McpDiscoveredTool(
        String name,
        String description,
        String inputSchemaJson,
        boolean whitelisted
) {
}
