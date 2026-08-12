package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MenuGrantItemRequest(
        @NotNull Long menuId,
        List<String> actionCodes
) {
}
