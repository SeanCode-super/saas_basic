package com.saasbasics.platform.common.auth;

import java.util.UUID;

public record OrganizationAccessContext(
        UUID organizationPublicId,
        UUID engagementPublicId,
        UUID orgUnitPublicId,
        UUID positionPublicId,
        UUID assignmentPublicId
) {
}
