package com.saasbasics.platform.modules.audit.dto;

import jakarta.validation.constraints.NotBlank;

public record RiskEvaluateRequest(
        @NotBlank(message = "规则编码不能为空") String ruleCode,
        String sampleAccount,
        String sampleIp,
        Integer frequency
) {
}
