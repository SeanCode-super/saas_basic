package com.saasbasics.platform.modules.ai.application.dto;

public record VaultRotateResultDTO(
        int rotatedProviders,
        int rotatedMcpServers,
        String activeKeyVersion,
        String message
) {
}
