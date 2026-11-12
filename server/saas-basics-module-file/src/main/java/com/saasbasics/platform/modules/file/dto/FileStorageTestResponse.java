package com.saasbasics.platform.modules.file.dto;

import java.util.List;

public record FileStorageTestResponse(
        Long storageId,
        String storageCode,
        String storageName,
        String storageType,
        boolean reachable,
        String summary,
        List<CheckStep> steps
) {
    public record CheckStep(
            String step,
            String status,
            String latency,
            String detail
    ) {
    }
}
