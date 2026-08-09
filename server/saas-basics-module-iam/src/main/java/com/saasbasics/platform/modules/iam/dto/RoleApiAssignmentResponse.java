package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record RoleApiAssignmentResponse(
        Long roleId,
        Long tenantId,
        List<Long> apiResourceIds
) {
}
