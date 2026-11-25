package com.saasbasics.platform.common.api;

import java.util.Optional;

/** CRM-owned view of recent customer activity visible to the caller. */
public interface CustomerFollowUpDirectory {
    record Summary(String customerName, int recentContracts, int recentFollowUps) {}

    Optional<Summary> recent(long tenantId, long customerId);
}
