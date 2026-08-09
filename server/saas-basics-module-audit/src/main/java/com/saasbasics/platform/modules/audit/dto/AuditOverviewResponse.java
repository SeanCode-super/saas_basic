package com.saasbasics.platform.modules.audit.dto;

public record AuditOverviewResponse(
        Long loginEventsToday,
        Long operationEventsToday,
        Long riskMatchesToday,
        Long activeBlackRules
) {
}
