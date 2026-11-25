package com.saasbasics.platform.common.file;

/** Checks restoration against storage reservations in the same transaction as the logical restore. */
public interface TenantFileRestoreCapacity {
    void requireCapacity(Long tenantId, Long fileId);
}
