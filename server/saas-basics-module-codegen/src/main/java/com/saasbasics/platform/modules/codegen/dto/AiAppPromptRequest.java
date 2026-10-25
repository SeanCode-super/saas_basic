package com.saasbasics.platform.modules.codegen.dto;

import jakarta.validation.constraints.NotBlank;

public record AiAppPromptRequest(
        @NotBlank(message = "Prompt cannot be blank")
        String prompt,
        String model,
        String category
) {
}
