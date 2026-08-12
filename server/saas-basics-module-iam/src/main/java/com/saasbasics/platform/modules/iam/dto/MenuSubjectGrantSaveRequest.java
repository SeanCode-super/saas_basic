package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MenuSubjectGrantSaveRequest(
        @NotBlank String subjectType,
        @NotBlank String subjectValue,
        @NotNull List<@Valid MenuGrantItemRequest> grants
) {
}
