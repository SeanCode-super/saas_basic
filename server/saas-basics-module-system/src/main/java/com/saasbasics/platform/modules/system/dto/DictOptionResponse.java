package com.saasbasics.platform.modules.system.dto;

public record DictOptionResponse(
        String dictCode,
        String label,
        String value,
        String color,
        String tag,
        boolean defaultItem
) {
}
