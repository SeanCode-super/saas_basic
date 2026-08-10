package com.saasbasics.platform.modules.organization.domain.model;

import com.saasbasics.platform.common.exception.BizException;
import java.time.Instant;

public record EffectivePeriod(Instant validFrom, Instant validTo) {

    public EffectivePeriod {
        if (validFrom == null) {
            throw new BizException("ORG_VALID_FROM_REQUIRED", "validFrom is required");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new BizException("ORG_PERIOD_INVALID", "validTo must be later than validFrom");
        }
    }

    public boolean contains(Instant instant) {
        if (instant == null) {
            return false;
        }
        return !instant.isBefore(validFrom) && (validTo == null || instant.isBefore(validTo));
    }

    public boolean isWithin(EffectivePeriod containingPeriod) {
        if (containingPeriod == null || validFrom.isBefore(containingPeriod.validFrom())) {
            return false;
        }
        if (containingPeriod.validTo() == null) {
            return true;
        }
        return validTo != null && !validTo.isAfter(containingPeriod.validTo());
    }
}
