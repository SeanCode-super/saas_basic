package com.saasbasics.platform.modules.tenant.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TenantSnapshotRestoreResponse(
        Long tenantId,
        String tenantCode,
        String snapshotId,
        String restoreBatchNo,
        String status,
        Map<String, Long> restoredCounts,
        LocalDateTime restoredAt,
        String message
) {}
