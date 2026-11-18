package com.saasbasics.platform.modules.iam.dto;

public record DataPermissionSqlPreviewResponse(
        String originalSql,
        String rewrittenSql,
        String scopeType,
        Long tenantId,
        long elapsedMicros
) {
}
