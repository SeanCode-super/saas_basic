package com.saasbasics.platform.modules.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobAlarmSaveRequest(
        @NotNull Long tenantId,
        @NotNull Long jobId,
        @NotBlank String alarmCode,
        @NotBlank String alarmName,
        @NotBlank String channelType,
        @NotBlank String triggerRule,
        String receiverJson,
        String templateCode,
        @NotNull Integer silenceMinutes,
        @NotBlank String status,
        String remark
) {
}
