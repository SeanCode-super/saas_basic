package com.saasbasics.platform.modules.iam.dto;

public record DataScopeResponse(
        Long id,
        Long tenantId,
        String scopeCode,
        String scopeName,
        String scopeType,
        String scopeRuleJson,
        String status,
        String remark
) {
}
