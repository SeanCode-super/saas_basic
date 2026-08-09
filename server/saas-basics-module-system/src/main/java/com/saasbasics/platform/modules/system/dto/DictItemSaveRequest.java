package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DictItemSaveRequest(
        @NotNull Long tenantId,
        @NotNull Long dictTypeId,
        @NotBlank String itemValue,
        @NotBlank String itemLabel,
        String itemColor,
        String itemTag,
        Long parentId,
        Integer sortNo,
        @NotBlank String status,
        boolean defaultItem,
        String extJson,
        String remark
) {
}
