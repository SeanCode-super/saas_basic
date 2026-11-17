package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiMcpServerDTO(
        Long id,
        Long tenantId,
        String serverCode,
        String serverName,
        String transportType,
        String endpointUrl,
        String authHeaderName,
        boolean hasAuthToken,
        String authTokenMasked,
        List<String> allowedTools,
        String healthStatus,
        LocalDateTime lastCheckedAt,
        String status,
        String remark
) {
}
