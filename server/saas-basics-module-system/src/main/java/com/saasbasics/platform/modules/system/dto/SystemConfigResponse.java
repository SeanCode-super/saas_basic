package com.saasbasics.platform.modules.system.dto;

public record SystemConfigResponse(
        Long id,
        Long tenantId,
        String group,
        String key,
        String name,
        String value,
        String valueType,
        String status,
        String remark
) {
}
