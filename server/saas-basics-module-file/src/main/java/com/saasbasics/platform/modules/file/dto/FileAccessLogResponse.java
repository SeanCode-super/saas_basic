package com.saasbasics.platform.modules.file.dto;

import java.time.LocalDateTime;

public record FileAccessLogResponse(
        Long id,
        Long tenantId,
        Long fileId,
        String accessType,
        Long operatorUserId,
        String operatorIp,
        Boolean success,
        LocalDateTime occurredAt,
        String remark
) {
}
