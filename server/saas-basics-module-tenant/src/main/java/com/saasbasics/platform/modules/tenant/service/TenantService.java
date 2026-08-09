package com.saasbasics.platform.modules.tenant.service;

import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSaveRequest;
import com.saasbasics.platform.modules.tenant.dto.TenantStatusUpdateRequest;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final TenantBootstrapService tenantBootstrapService;
    private final AuditTrailService auditTrailService;

    public TenantService(ObjectProvider<TenantMapper> tenantMapperProvider,
                         TenantBootstrapService tenantBootstrapService,
                         AuditTrailService auditTrailService) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.tenantBootstrapService = tenantBootstrapService;
        this.auditTrailService = auditTrailService;
    }

    public PageResponse<TenantResponse> pageTenants() {
        return PageResponse.of(requiredMapper().selectTenantPage());
    }

    public TenantResponse getTenant(Long id) {
        TenantMapper tenantMapper = requiredMapper();
        TenantResponse tenant = tenantMapper.selectTenantById(id);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }
        return tenant;
    }

    @Transactional
    public TenantResponse createTenant(TenantSaveRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity exists = tenantMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getTenantCode, request.tenantCode())
                .eq(TenantEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BizException("TENANT_CODE_DUPLICATE", "租户编码已存在");
        }
        TenantEntity entity = new TenantEntity();
        apply(entity, request);
        tenantMapper.insert(entity);
        tenantBootstrapService.initializeTenant(entity.getId(), entity.getTenantCode(), entity.getTenantName());
        TenantResponse response = getTenant(entity.getId());
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "CREATE", request.tenantCode(), response.tenantCode(), true);
        return response;
    }

    @Transactional
    public TenantResponse updateTenant(Long id, TenantSaveRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }
        apply(entity, request);
        tenantMapper.updateById(entity);
        TenantResponse response = getTenant(id);
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "UPDATE", request.tenantCode(), response.tenantCode(), true);
        return response;
    }

    @Transactional
    public TenantResponse updateStatus(Long id, TenantStatusUpdateRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }
        entity.setStatus(request.status());
        tenantMapper.updateById(entity);
        TenantResponse response = getTenant(id);
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "STATUS", request.status(), response.status(), true);
        return response;
    }

    private void apply(TenantEntity entity, TenantSaveRequest request) {
        entity.setTenantCode(request.tenantCode());
        entity.setTenantName(request.tenantName());
        entity.setPackageId(request.packageId());
        entity.setStatus(request.status());
        entity.setIsolationMode(request.isolationMode());
        entity.setRemark(request.remark());
    }

    private TenantMapper requiredMapper() {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        if (tenantMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return tenantMapper;
    }
}
