package com.saasbasics.platform.modules.tenant.dto;

import java.time.LocalDateTime;

public record TenantAppResponse(
        Long id,
        Long tenantId,
        String appCode,
        String appName,
        String status,
        LocalDateTime openedAt,
        LocalDateTime expiredAt,
        String configJson,
        String remark
) {
}
