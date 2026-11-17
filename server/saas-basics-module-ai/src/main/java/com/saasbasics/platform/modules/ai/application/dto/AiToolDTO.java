package com.saasbasics.platform.modules.ai.application.dto;

public record AiToolDTO(
        Long id,
        Long tenantId,
        String toolCode,
        String toolName,
        String semanticVersion,
        String description,
        String inputSchemaJson,
        String permissionCode,
        String riskLevel,
        String adapterType,
        Long mcpServerId,
        boolean whitelisted,
        boolean idempotent,
        Integer timeoutMs,
        String status
) {
}
