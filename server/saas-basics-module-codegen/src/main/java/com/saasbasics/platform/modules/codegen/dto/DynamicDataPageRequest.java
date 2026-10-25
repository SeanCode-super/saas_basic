package com.saasbasics.platform.modules.codegen.dto;

import java.util.Map;

public record DynamicDataPageRequest(
        Integer page,
        Integer size,
        String keyword,
        Map<String, Object> filters
) {
    public int getPage() {
        return (page != null && page > 0) ? page : 1;
    }

    public int getSize() {
        return (size != null && size > 0 && size <= 200) ? size : 10;
    }
}
