package com.saasbasics.platform.common.tenant;

/** IAM-owned navigation for a tenant's published dynamic model. */
public interface DynamicApplicationMenus {
    long register(long tenantId, String modelCode, String name, String icon, long actorId);
    boolean canOpen(long menuId);
    void retire(long tenantId, long menuId, long actorId);
}
