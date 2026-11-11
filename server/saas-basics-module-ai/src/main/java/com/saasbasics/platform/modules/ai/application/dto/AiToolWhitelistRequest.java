package com.saasbasics.platform.modules.ai.application.dto;

public record AiToolWhitelistRequest(
        Boolean whitelisted,
        String riskLevel
) {
}
