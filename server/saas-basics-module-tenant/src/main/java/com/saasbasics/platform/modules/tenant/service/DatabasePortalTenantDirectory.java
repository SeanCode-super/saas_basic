package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.tenant.api.PortalTenantDirectory;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DatabasePortalTenantDirectory implements PortalTenantDirectory {
    private final TenantMapper tenants;

    public DatabasePortalTenantDirectory(TenantMapper tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<PortalTenant> findByCode(String code) {
        return find(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getTenantCode, code));
    }

    @Override
    public Optional<PortalTenant> findById(Long id) {
        return find(new LambdaQueryWrapper<TenantEntity>().eq(TenantEntity::getId, id));
    }

    private Optional<PortalTenant> find(LambdaQueryWrapper<TenantEntity> query) {
        query.select(TenantEntity::getId, TenantEntity::getTenantCode, TenantEntity::getTenantName, TenantEntity::getStatus)
                .eq(TenantEntity::getDeleted, 0);
        return Optional.ofNullable(tenants.selectOne(query))
                .map(row -> new PortalTenant(row.getId(), row.getTenantCode(), row.getTenantName(), row.getStatus()));
    }
}
