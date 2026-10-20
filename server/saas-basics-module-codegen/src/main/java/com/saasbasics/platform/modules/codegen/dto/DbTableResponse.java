package com.saasbasics.platform.modules.codegen.dto;

public record DbTableResponse(
        String tableName,
        String tableComment,
        String createTime
) {}
