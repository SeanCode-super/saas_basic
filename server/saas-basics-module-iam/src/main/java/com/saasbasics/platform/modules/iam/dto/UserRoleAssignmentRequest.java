package com.saasbasics.platform.modules.iam.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleAssignmentRequest(
        @NotNull Long tenantId,
        List<Long> roleIds
) {
}
