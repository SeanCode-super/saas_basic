package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record RoleUserAssignmentResponse(
        Long roleId,
        Long tenantId,
        List<Long> userIds
) {
}
