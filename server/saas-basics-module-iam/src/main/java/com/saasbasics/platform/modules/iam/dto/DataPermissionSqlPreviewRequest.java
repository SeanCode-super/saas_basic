package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record DataPermissionSqlPreviewRequest(
        @NotBlank(message = "待测试 SQL 不能为空") String sql,
        String resourceCode,
        Long tenantId
) {
}
