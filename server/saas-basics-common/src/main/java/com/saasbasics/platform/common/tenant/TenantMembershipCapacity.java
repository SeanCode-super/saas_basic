package com.saasbasics.platform.common.tenant;

/** Checks capacity under the same tenant transaction that inserts the member or department. */
public interface TenantMembershipCapacity {
    void requireSeat(Long tenantId);

    void requireDepartment(Long tenantId);
}
