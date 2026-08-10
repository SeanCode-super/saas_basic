package com.saasbasics.platform.modules.iam.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stable cross-module read contract for account-to-person associations. */
public interface UserPersonDirectory {

    Optional<BindingSummary> findEffectiveByUser(UUID userPublicId, Instant effectiveAt);

    List<BindingSummary> findEffectiveByPerson(UUID personPublicId, Instant effectiveAt);

    record BindingSummary(
            UUID publicId,
            UUID userPublicId,
            UUID personPublicId,
            Instant validFrom,
            Instant validTo,
            int version
    ) {
    }
}
