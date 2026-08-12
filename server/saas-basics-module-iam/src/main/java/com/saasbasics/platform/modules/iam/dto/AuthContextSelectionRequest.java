package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AuthContextSelectionRequest(@NotNull UUID assignmentPublicId) {
}
