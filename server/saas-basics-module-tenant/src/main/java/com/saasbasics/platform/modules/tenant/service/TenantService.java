package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.api.PageResponse;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.tenant.dto.TenantPlanUpdateRequest;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaOverviewResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantSaveRequest;
import com.saasbasics.platform.modules.tenant.dto.TenantStatusUpdateRequest;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final ObjectProvider<TenantMapper> tenantMapperProvider;
    private final TenantBootstrapService tenantBootstrapService;
    private final TenantQuotaService tenantQuotaService;
    private final AuditTrailService auditTrailService;

    public TenantService(ObjectProvider<TenantMapper> tenantMapperProvider,
                          TenantBootstrapService tenantBootstrapService,
                          TenantQuotaService tenantQuotaService,
                          AuditTrailService auditTrailService) {
        this.tenantMapperProvider = tenantMapperProvider;
        this.tenantBootstrapService = tenantBootstrapService;
        this.tenantQuotaService = tenantQuotaService;
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

    public TenantQuotaOverviewResponse getQuotaOverview(Long id) {
        return tenantQuotaService.getTenantQuotaOverview(id);
    }

    @Transactional
    public TenantResponse createTenant(TenantSaveRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity exists = tenantMapper.selectOne(new LambdaQueryWrapper<TenantEntity>()
                .eq(TenantEntity::getTenantCode, request.tenantCode())
                .eq(TenantEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BizException("TENANT_CODE_DUPLICATE", "租户编码已存在");
        }
        TenantEntity entity = new TenantEntity();
        apply(entity, request);
        tenantMapper.insert(entity);

        // Initialize tenant quotas and baseline
        tenantQuotaService.initTenantQuotas(entity.getId(), entity.getPackageId());

        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openPlatformBypass(
                "tenant:write",
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP,
                "Initialize required records for tenant " + entity.getTenantCode())) {
            tenantBootstrapService.initializeTenant(entity.getId(), entity.getTenantCode(), entity.getTenantName());
        }
        TenantResponse response = getTenant(entity.getId());
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "CREATE", request.tenantCode(), response.tenantCode(), true);
        return response;
    }

    @Transactional
    public TenantResponse updateTenant(Long id, TenantSaveRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }
        Long oldPackageId = entity.getPackageId();
        apply(entity, request);
        tenantMapper.updateById(entity);

        if (!oldPackageId.equals(entity.getPackageId())) {
            tenantQuotaService.initTenantQuotas(id, entity.getPackageId());
        }

        TenantResponse response = getTenant(id);
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "UPDATE", request.tenantCode(), response.tenantCode(), true);
        return response;
    }

    @Transactional
    public TenantResponse updateStatus(Long id, TenantStatusUpdateRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }
        entity.setStatus(request.status());
        tenantMapper.updateById(entity);
        TenantResponse response = getTenant(id);
        auditTrailService.record("tenant", "tenant", String.valueOf(response.id()), "STATUS", request.status(), response.status(), true);
        return response;
    }

    @Transactional
    public TenantResponse updateTenantPlan(Long id, TenantPlanUpdateRequest request) {
        TenantMapper tenantMapper = requiredMapper();
        TenantEntity entity = tenantMapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("TENANT_NOT_FOUND", "Tenant not found");
        }

        entity.setPackageId(request.packageId());
        if (request.expireAt() != null && !request.expireAt().isBlank()) {
            entity.setExpireAt(parseDateTime(request.expireAt()));
            entity.setStatus("ACTIVE");
        }
        if (request.remark() != null) {
            entity.setRemark(request.remark());
        }
        tenantMapper.updateById(entity);

        // Update quota thresholds to match new package
        tenantQuotaService.initTenantQuotas(id, request.packageId());

        TenantResponse response = getTenant(id);
        auditTrailService.record("tenant", "tenant_plan", String.valueOf(id), "UPGRADE_PLAN",
                String.valueOf(request.packageId()), response.packageName(), true);
        return response;
    }

    private void apply(TenantEntity entity, TenantSaveRequest request) {
        entity.setTenantCode(request.tenantCode());
        entity.setTenantName(request.tenantName());
        entity.setPackageId(request.packageId());
        entity.setStatus(request.status());
        entity.setIsolationMode(request.isolationMode());
        if (request.expireAt() != null && !request.expireAt().isBlank()) {
            entity.setExpireAt(parseDateTime(request.expireAt()));
        } else if (entity.getId() == null) {
            // Default 365 days for new tenant
            entity.setExpireAt(LocalDateTime.now().plusDays(365));
        }
        entity.setContactName(request.contactName());
        entity.setContactMobile(request.contactMobile());
        entity.setContactEmail(request.contactEmail());
        entity.setRemark(request.remark());
    }

    private LocalDateTime parseDateTime(String text) {
        try {
            if (text.length() == 10) {
                return LocalDateTime.parse(text + "T23:59:59");
            }
            if (text.contains(" ")) {
                return LocalDateTime.parse(text.replace(" ", "T"));
            }
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            return LocalDateTime.now().plusDays(365);
        }
    }

    private TenantMapper requiredMapper() {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        if (tenantMapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return tenantMapper;
    }
}
