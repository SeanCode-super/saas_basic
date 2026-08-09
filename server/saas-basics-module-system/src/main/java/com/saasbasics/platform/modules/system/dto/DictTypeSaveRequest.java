package com.saasbasics.platform.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DictTypeSaveRequest(
        @NotNull Long tenantId,
        @NotBlank String dictCode,
        @NotBlank String dictName,
        @NotBlank String dictScope,
        @NotBlank String status,
        boolean cacheable,
        String extJson,
        String remark
) {
}
