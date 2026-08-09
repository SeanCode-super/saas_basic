package com.saasbasics.platform.modules.integration.dto;

public record DatasourceResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String type,
        String usageType,
        String host,
        Integer port,
        String databaseName,
        String username,
        String testStatus,
        String status,
        String remark
) {
}
