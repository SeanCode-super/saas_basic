package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.NotBlank;

public record SequenceRuleSaveRequest(
        @NotBlank(message = "规则名称不能为空") String name,
        String prefix,
        String datePattern,
        Integer seqLength,
        Integer step,
        String resetCycle,
        String status
) {
}
