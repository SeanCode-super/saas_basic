package com.saasbasics.platform.modules.ai.application.dto;

public record AiTenantQuotaSaveRequest(
        Long monthlyTokenLimit,
        Integer rpmLimit
) {
}
