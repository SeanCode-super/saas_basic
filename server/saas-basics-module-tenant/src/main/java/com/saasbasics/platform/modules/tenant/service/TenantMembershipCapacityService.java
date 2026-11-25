package com.saasbasics.platform.modules.tenant.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.common.tenant.TenantQuotaWriteGuard;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import org.springframework.stereotype.Service;

@Service
public class TenantMembershipCapacityService implements TenantMembershipCapacity {
    private final TenantQuotaWriteGuard guard;
    private final PlatTenantMeteringMapper metering;
    private final TenantQuotaMapper quotas;

    public TenantMembershipCapacityService(TenantQuotaWriteGuard guard, PlatTenantMeteringMapper metering,
                                          TenantQuotaMapper quotas) {
        this.guard = guard;
        this.metering = metering;
        this.quotas = quotas;
    }

    @Override
    public void requireSeat(Long tenantId) {
        long limit = guard.lockLimit(tenantId, "USER_COUNT");
        if (metering.countActiveSeats(tenantId) >= limit) {
            throw new BizException("TENANT_USER_QUOTA_EXCEEDED", "账号席位已达套餐上限");
        }
    }

    @Override
    public void requireDepartment(Long tenantId) {
        long limit = guard.lockLimit(tenantId, "DEPT_COUNT");
        if (quotas.countActiveDepartments(tenantId) >= limit) {
            throw new BizException("TENANT_DEPT_QUOTA_EXCEEDED", "部门数量已达套餐上限");
        }
    }
}
