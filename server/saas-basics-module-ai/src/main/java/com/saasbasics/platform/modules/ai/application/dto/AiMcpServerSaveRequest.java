package com.saasbasics.platform.modules.ai.application.dto;

import java.util.List;

public record AiMcpServerSaveRequest(
        Long id,
        String serverCode,
        String serverName,
        String transportType,
        String endpointUrl,
        String authHeaderName,
        String authToken,
        List<String> allowedTools,
        String status,
        String remark
) {
}
