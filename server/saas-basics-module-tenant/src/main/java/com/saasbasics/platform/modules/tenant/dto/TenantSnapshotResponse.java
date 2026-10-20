package com.saasbasics.platform.modules.tenant.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TenantSnapshotResponse(
        Long tenantId,
        String tenantCode,
        String snapshotId,
        String checksumSha256,
        Long archiveSizeBytes,
        Map<String, Long> entityCounts,
        LocalDateTime generatedAt,
        String downloadUrl
) {}
