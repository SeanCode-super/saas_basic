package com.saasbasics.platform.modules.tenant.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.common.tenant.TenantQuotaWriteGuard;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQuotaWriteGuardImpl implements TenantQuotaWriteGuard {

    private final TenantQuotaMapper mapper;

    public TenantQuotaWriteGuardImpl(TenantQuotaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long lockLimit(Long tenantId, String quotaCode) {
        TenantAccessContext scope = TenantAccessContextHolder.current();
        boolean platformBootstrap = scope != null && scope.mode() == TenantAccessContext.Mode.PLATFORM_BYPASS
                && scope.bypassOperation() == TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP;
        if (scope == null || tenantId == null || (!platformBootstrap && !tenantId.equals(scope.tenantId()))) {
            throw new BizException("TENANT_ACCESS_DENIED", "配额操作需要目标租户上下文");
        }
        Long limit = mapper.lockQuotaLimit(tenantId, quotaCode);
        if (limit == null) {
            throw new BizException("TENANT_QUOTA_NOT_CONFIGURED", "租户配额尚未配置：" + quotaCode);
        }
        return limit;
    }
}
