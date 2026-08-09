package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record UserRoleAssignmentResponse(
        Long userId,
        Long tenantId,
        List<Long> roleIds
) {
}
