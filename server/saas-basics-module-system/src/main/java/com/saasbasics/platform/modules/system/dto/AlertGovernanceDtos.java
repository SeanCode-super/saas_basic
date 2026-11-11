package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class AlertGovernanceDtos {

    private AlertGovernanceDtos() {
    }

    public record AlertRuleResponse(
            Long id,
            String ruleCode,
            String ruleName,
            String metricType,
            String comparisonOp,
            double thresholdValue,
            String severity,
            int cooldownSeconds,
            String notifyChannels,
            String webhookUrl,
            boolean enabled,
            LocalDateTime lastTriggeredAt
    ) {
    }

    public record AlertRuleSaveRequest(
            @NotBlank String ruleCode,
            @NotBlank String ruleName,
            @NotBlank String metricType,
            String comparisonOp,
            @Min(0) double thresholdValue,
            String severity,
            @Min(10) Integer cooldownSeconds,
            String notifyChannels,
            String webhookUrl,
            Boolean enabled
    ) {
    }

    public record AlertEventResponse(
            Long id,
            Long ruleId,
            String ruleCode,
            String ruleName,
            String severity,
            String metricType,
            double metricValue,
            double thresholdValue,
            String targetEndpoint,
            String message,
            String dispatchStatus,
            String dispatchDetail,
            boolean acknowledged,
            String acknowledgedBy,
            LocalDateTime acknowledgedAt,
            LocalDateTime triggeredAt
    ) {
    }

    public record AlertEvaluationSummary(
            int evaluatedRules,
            int triggeredEvents,
            List<AlertEventResponse> newEvents
    ) {
    }
}
