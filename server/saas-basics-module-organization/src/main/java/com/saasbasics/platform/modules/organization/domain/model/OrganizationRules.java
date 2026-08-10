package com.saasbasics.platform.modules.organization.domain.model;

import com.saasbasics.platform.common.exception.BizException;
import java.time.Instant;
import java.util.regex.Pattern;

public final class OrganizationRules {

    private static final Pattern RESOURCE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private OrganizationRules() {
    }

    public static String requireResourceCode(String value, String field) {
        if (value == null || !RESOURCE_CODE.matcher(value.trim()).matches()) {
            throw new BizException(
                    "ORG_RESOURCE_CODE_INVALID",
                    field + " must contain only portable code characters and be at most 128 characters"
            );
        }
        return value.trim();
    }

    public static String requireDisplayName(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 256) {
            throw new BizException("ORG_DISPLAY_NAME_INVALID", field + " must contain 1 to 256 characters");
        }
        return value.trim();
    }

    public static EffectivePeriod requirePeriod(Instant validFrom, Instant validTo) {
        return new EffectivePeriod(validFrom, validTo);
    }

    public static void requireDistinct(long sourceId, long targetId, String resourceName) {
        if (sourceId == targetId) {
            throw new BizException("ORG_SELF_RELATION_FORBIDDEN", resourceName + " cannot relate a resource to itself");
        }
    }

    public static void requireSameOrganization(long expectedId, long actualId, String resourceName) {
        if (expectedId != actualId) {
            throw new BizException(
                    "ORG_CROSS_ORGANIZATION_REFERENCE",
                    resourceName + " must reference resources owned by the same organization"
            );
        }
    }
}
