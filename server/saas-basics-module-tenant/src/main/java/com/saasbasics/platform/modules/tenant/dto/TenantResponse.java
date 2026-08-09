package com.saasbasics.platform.modules.tenant.dto;

public record TenantResponse(
        Long id,
        String tenantCode,
        String tenantName,
        Long packageId,
        String packageName,
        String status,
        String isolationMode,
        String remark
) {
}
