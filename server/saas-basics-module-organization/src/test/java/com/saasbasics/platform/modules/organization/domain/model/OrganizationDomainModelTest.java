package com.saasbasics.platform.modules.organization.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbasics.platform.common.exception.BizException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrganizationDomainModelTest {

    @Test
    void effectivePeriodsUseLeftClosedRightOpenSemantics() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2027-01-01T00:00:00Z");
        EffectivePeriod period = new EffectivePeriod(start, end);

        assertThat(period.contains(start)).isTrue();
        assertThat(period.contains(end.minusMillis(1))).isTrue();
        assertThat(period.contains(end)).isFalse();
    }

    @Test
    void rejectsEmptyAndReversedPeriods() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> new EffectivePeriod(start, start))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("ORG_PERIOD_INVALID");
    }

    @Test
    void validatesNamespacedSemanticVersionedTypes() {
        TypeDescriptor descriptor = new TypeDescriptor(
                "urn:saas-basics:organization",
                "MEMBER_OF",
                "1.0.0"
        );

        assertThat(descriptor.namespace()).isEqualTo("urn:saas-basics:organization");
        assertThatThrownBy(() -> new TypeDescriptor("relative/path", "MEMBER_OF", "1.0"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new TypeDescriptor(
                "urn:saas-basics:organization", "MEMBER_OF", "1.0.0-01"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void lifecycleAllowsOnlyDefinedTransitionsAndRecovery() {
        LifecycleStatus.DRAFT.requireTransitionTo(LifecycleStatus.ACTIVE);
        LifecycleStatus.ARCHIVED.requireTransitionTo(LifecycleStatus.INACTIVE);

        assertThatThrownBy(() -> LifecycleStatus.ACTIVE.requireTransitionTo(LifecycleStatus.ARCHIVED))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo("ORG_LIFECYCLE_TRANSITION_INVALID");
    }
}
