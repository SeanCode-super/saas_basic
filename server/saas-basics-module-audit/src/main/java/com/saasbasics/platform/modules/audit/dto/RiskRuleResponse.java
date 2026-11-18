package com.saasbasics.platform.modules.audit.dto;

public record RiskRuleResponse(
        Long id,
        String code,
        String name,
        String severity,
        String ruleExpr,
        String action,
        String description,
        boolean enabled
) {
}
