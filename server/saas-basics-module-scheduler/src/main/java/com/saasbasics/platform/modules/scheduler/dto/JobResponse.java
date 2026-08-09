package com.saasbasics.platform.modules.scheduler.dto;

public record JobResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String jobType,
        Long executorId,
        String handlerName,
        String status,
        String remark
) {
}
