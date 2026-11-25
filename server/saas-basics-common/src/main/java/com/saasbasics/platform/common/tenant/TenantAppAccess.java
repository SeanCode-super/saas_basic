package com.saasbasics.platform.common.tenant;

public interface TenantAppAccess {
    boolean isInstalled(long tenantId, String code, int minimumRevision);
}
