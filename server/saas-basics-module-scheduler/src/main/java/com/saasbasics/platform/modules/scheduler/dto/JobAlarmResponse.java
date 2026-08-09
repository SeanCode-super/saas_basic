package com.saasbasics.platform.modules.scheduler.dto;

public record JobAlarmResponse(
        Long id,
        Long tenantId,
        Long jobId,
        String alarmCode,
        String alarmName,
        String channelType,
        String triggerRule,
        String receiverJson,
        String templateCode,
        Integer silenceMinutes,
        String status,
        String remark
) {
}
