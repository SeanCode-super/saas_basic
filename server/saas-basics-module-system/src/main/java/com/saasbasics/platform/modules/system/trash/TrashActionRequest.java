package com.saasbasics.platform.modules.system.trash;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrashActionRequest(
        @NotBlank(message = "表名不能为空")
        String tableName,

        @NotNull(message = "记录ID不能为空")
        Long recordId,

        Boolean cascade
) {
    public boolean isCascade() {
        return cascade == null || cascade;
    }
}
