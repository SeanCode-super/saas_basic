package com.saasbasics.platform.modules.organization.domain.model;

import com.saasbasics.platform.common.exception.BizException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum LifecycleStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED;

    private static final Map<LifecycleStatus, Set<LifecycleStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ACTIVE, ARCHIVED),
            ACTIVE, EnumSet.of(INACTIVE),
            INACTIVE, EnumSet.of(ACTIVE, ARCHIVED),
            ARCHIVED, EnumSet.of(INACTIVE)
    );

    public void requireTransitionTo(LifecycleStatus target) {
        if (target == null) {
            throw new BizException("ORG_LIFECYCLE_TARGET_REQUIRED", "A target lifecycle status is required");
        }
        if (this == target) {
            return;
        }
        if (!TRANSITIONS.get(this).contains(target)) {
            throw new BizException(
                    "ORG_LIFECYCLE_TRANSITION_INVALID",
                    "Lifecycle transition from " + this + " to " + target + " is not allowed"
            );
        }
    }
}
