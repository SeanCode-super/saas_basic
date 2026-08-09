package com.saasbasics.platform.modules.scheduler.dto;

import java.time.LocalDateTime;

public record JobLogResponse(
        Long id,
        Long tenantId,
        Long jobId,
        String executionNo,
        Long executorId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String runStatus,
        Boolean success,
        Integer retryCount,
        String errorMessage,
        String traceId
) {
}
