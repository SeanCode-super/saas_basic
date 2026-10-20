package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageSaveRequest;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPackageService {

    private final TenantPackageMapper packageMapper;
    private final AuditTrailService auditTrailService;

    public TenantPackageService(TenantPackageMapper packageMapper, AuditTrailService auditTrailService) {
        this.packageMapper = packageMapper;
        this.auditTrailService = auditTrailService;
    }

    public List<TenantPackageResponse> listPackages() {
        return packageMapper.selectPackageList();
    }

    public TenantPackageResponse getPackage(Long id) {
        TenantPackageResponse pkg = packageMapper.selectPackageById(id);
        if (pkg == null) {
            throw new BizException("PACKAGE_NOT_FOUND", "套餐不存在");
        }
        return pkg;
    }

    @Transactional
    public TenantPackageResponse createPackage(TenantPackageSaveRequest request) {
        TenantPackageEntity exists = packageMapper.selectOne(new LambdaQueryWrapper<TenantPackageEntity>()
                .eq(TenantPackageEntity::getPackageCode, request.packageCode())
                .eq(TenantPackageEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BizException("PACKAGE_CODE_DUPLICATE", "套餐编码已存在");
        }
        TenantPackageEntity entity = new TenantPackageEntity();
        apply(entity, request);
        packageMapper.insert(entity);
        auditTrailService.record("tenant_package", "package", String.valueOf(entity.getId()), "CREATE", request.packageCode(), entity.getPackageName(), true);
        return getPackage(entity.getId());
    }

    @Transactional
    public TenantPackageResponse updatePackage(Long id, TenantPackageSaveRequest request) {
        TenantPackageEntity entity = packageMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("PACKAGE_NOT_FOUND", "套餐不存在");
        }
        apply(entity, request);
        packageMapper.updateById(entity);
        auditTrailService.record("tenant_package", "package", String.valueOf(id), "UPDATE", request.packageCode(), entity.getPackageName(), true);
        return getPackage(id);
    }

    @Transactional
    public void deletePackage(Long id) {
        TenantPackageResponse pkg = getPackage(id);
        if (pkg.activeTenantCount() != null && pkg.activeTenantCount() > 0) {
            throw new BizException("PACKAGE_IN_USE", "当前套餐仍有活跃租户关联，不可删除");
        }
        packageMapper.deleteById(id);
        auditTrailService.record("tenant_package", "package", String.valueOf(id), "DELETE", pkg.packageCode(), pkg.packageName(), true);
    }

    private void apply(TenantPackageEntity entity, TenantPackageSaveRequest request) {
        entity.setPackageCode(request.packageCode());
        entity.setPackageName(request.packageName());
        entity.setPackageType(request.packageType() == null || request.packageType().isBlank() ? "STANDARD" : request.packageType());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "ENABLED" : request.status());
        entity.setMaxUserCount(request.maxUserCount());
        entity.setMaxDeptCount(request.maxDeptCount());
        entity.setMaxStorageMb(request.maxStorageMb());
        entity.setMaxApiPerMinute(request.maxApiPerMinute());
        entity.setFeatureFlagsJson(request.featureFlagsJson() == null || request.featureFlagsJson().isBlank() ? "[]" : request.featureFlagsJson());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
    }
}
