package com.saasbasics.platform.common.tenant;

/** Serializes consumption of one quota until the caller's business transaction completes. */
public interface TenantQuotaWriteGuard {
    long lockLimit(Long tenantId, String quotaCode);
}
