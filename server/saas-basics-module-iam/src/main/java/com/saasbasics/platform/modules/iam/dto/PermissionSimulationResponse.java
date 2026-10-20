package com.saasbasics.platform.modules.iam.dto;

import java.util.List;

public record PermissionSimulationResponse(
        boolean overallGranted,
        boolean actionGranted,
        boolean dataScopeGranted,
        String scopeType,
        List<Long> allowedDepartmentIds,
        List<String> matchedRoles,
        List<String> decisionTrace,
        String simulatedSqlCondition
) {
}
