package com.saasbasics.platform.modules.system.dto;

public record SequenceRuleResponse(
        Long id,
        Long tenantId,
        String code,
        String name,
        String prefix,
        String datePattern,
        String pattern,
        Integer seqLength,
        Integer step,
        Long currentSeq,
        String resetCycle,
        String sample,
        String status
) {
}
