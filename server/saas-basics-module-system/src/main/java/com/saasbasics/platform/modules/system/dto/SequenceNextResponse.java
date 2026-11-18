package com.saasbasics.platform.modules.system.dto;

public record SequenceNextResponse(
        String code,
        Long currentSeq,
        String generatedValue
) {
}
