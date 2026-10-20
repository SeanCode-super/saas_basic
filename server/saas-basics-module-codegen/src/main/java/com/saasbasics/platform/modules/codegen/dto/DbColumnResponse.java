package com.saasbasics.platform.modules.codegen.dto;

public record DbColumnResponse(
        String columnName,
        String dataType,
        String columnType,
        String columnComment,
        String isNullable,
        String columnKey,
        String extra
) {}
