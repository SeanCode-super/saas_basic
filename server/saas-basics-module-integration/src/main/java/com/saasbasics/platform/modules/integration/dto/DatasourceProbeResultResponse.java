package com.saasbasics.platform.modules.integration.dto;

import java.time.LocalDateTime;

public record DatasourceProbeResultResponse(
        Long id,
        String code,
        String name,
        String testStatus,
        long latencyMs,
        String message,
        LocalDateTime testedAt
) {
}
