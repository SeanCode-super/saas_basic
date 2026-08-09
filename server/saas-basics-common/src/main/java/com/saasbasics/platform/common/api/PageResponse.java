package com.saasbasics.platform.common.api;

import java.util.List;

public record PageResponse<T>(List<T> records, long total) {

    public static <T> PageResponse<T> of(List<T> records) {
        return new PageResponse<>(records, records.size());
    }
}
