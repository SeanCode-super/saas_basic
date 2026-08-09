package com.saasbasics.platform.modules.iam.dto;

public record DataPermissionRuleResponse(
        Long id,
        Long tenantId,
        String resourceCode,
        String resourceName,
        String subjectType,
        String subjectValue,
        String scopeType,
        String configJson,
        String status,
        String remark
) {
}
