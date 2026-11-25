package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaItemResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaOverviewResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import com.saasbasics.platform.modules.tenant.mapper.PlatTenantMeteringMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQuotaService {

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantQuotaMapper quotaMapper;
    private final TenantPackageMapper packageMapper;
    private final TenantMapper tenantMapper;
    private final PlatTenantMeteringMapper meteringMapper;

    public TenantQuotaService(TenantQuotaMapper quotaMapper,
                              TenantPackageMapper packageMapper,
                              TenantMapper tenantMapper,
                              PlatTenantMeteringMapper meteringMapper) {
        this.quotaMapper = quotaMapper;
        this.packageMapper = packageMapper;
        this.tenantMapper = tenantMapper;
        this.meteringMapper = meteringMapper;
    }

    @Transactional
    public void initTenantQuotas(Long tenantId, Long packageId) {
        TenantPackageEntity pkg = packageMapper.selectById(packageId);
        if (pkg == null) {
            pkg = packageMapper.selectById(1L);
        }
        if (pkg == null) return;

        // 1. User seat quota
        upsertQuota(tenantId, "USER_COUNT", "账号席位上限", (long) pkg.getMaxUserCount(),
                (long) Math.floor(pkg.getMaxUserCount() * 0.8), "NONE", "基础席位配额");

        // 2. Department quota
        upsertQuota(tenantId, "DEPT_COUNT", "组织部门上限", (long) pkg.getMaxDeptCount(),
                (long) Math.floor(pkg.getMaxDeptCount() * 0.8), "NONE", "组织架构配额");

        // 3. Storage MB quota
        upsertQuota(tenantId, "STORAGE_MB", "文件存储空间(MB)", (long) pkg.getMaxStorageMb(),
                (long) Math.floor(pkg.getMaxStorageMb() * 0.85), "NONE", "存储空间配额");

        // 4. API Throttling
        upsertQuota(tenantId, "API_PER_MINUTE", "API每分钟调用限流", (long) pkg.getMaxApiPerMinute(),
                (long) Math.floor(pkg.getMaxApiPerMinute() * 0.9), "MINUTE", "流量防护限流");
    }

    public TenantQuotaOverviewResponse getTenantQuotaOverview(Long tenantId) {
        if (TenantAccessContextHolder.requiredTenantId().equals(tenantId)) {
            return readQuotaOverview(tenantId);
        }
        try (var scope = TenantAccessContextHolder.openPlatformBypass("tenant:query",
                TenantAccessContext.BypassOperation.PLATFORM_TENANT_ADMINISTRATION,
                "Read quota configuration for tenant " + tenantId)) {
            return readQuotaOverview(tenantId);
        }
    }

    private TenantQuotaOverviewResponse readQuotaOverview(Long tenantId) {
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "租户不存在");
        }
        TenantPackageEntity pkg = packageMapper.selectById(tenant.getPackageId());
        String packageName = pkg != null ? pkg.getPackageName() : "未分配套餐";

        List<TenantQuotaEntity> quotas = quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .orderByAsc(TenantQuotaEntity::getId));

        List<TenantQuotaItemResponse> items = new ArrayList<>(quotas.size());
        // Tenant-owned tables may be in the target's dedicated data source.
        try (var scope = TenantAccessContextHolder.openTenant(tenantId, tenant.getTenantCode(),
                TenantAccessContextHolder.current().actorId())) {
            for (TenantQuotaEntity quota : quotas) {
                items.add(readQuotaItem(tenantId, quota));
            }
        }

        // Calculate lifecycle
        LocalDateTime expireAt = tenant.getExpireAt();
        Long daysRemaining = null;
        String lifecycleStatus = tenant.getStatus();
        if (expireAt != null) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDateTime.now(), expireAt);
            if (daysRemaining < 0) {
                lifecycleStatus = "EXPIRED";
            } else if (daysRemaining <= 7 && "ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                lifecycleStatus = "EXPIRING";
            }
        }

        return new TenantQuotaOverviewResponse(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getTenantName(),
                tenant.getPackageId(),
                packageName,
                expireAt != null ? expireAt.format(EXPIRY_FORMAT) : "永久有效",
                daysRemaining,
                lifecycleStatus,
                items
        );
    }

    private TenantQuotaItemResponse readQuotaItem(Long tenantId, TenantQuotaEntity quota) {
        long quantity = switch (quota.getQuotaCode().toUpperCase(Locale.ROOT)) {
            case "USER_COUNT" -> meteringMapper.countActiveSeats(tenantId);
            case "DEPT_COUNT" -> quotaMapper.countActiveDepartments(tenantId);
            case "STORAGE_MB" -> meteringMapper.sumStorageUsed(tenantId);
            default -> quota.getUsedValue();
        };
        long unit = "STORAGE_MB".equalsIgnoreCase(quota.getQuotaCode()) ? BYTES_PER_MB : 1L;
        long max = quota.getQuotaValue();
        double usage = (double) quantity / unit;
        double percent = max > 0 ? Math.min(100.0, usage / max * 100.0) : (quantity > 0 ? 100.0 : 0.0);
        String status = percent >= 100 ? "DANGER" : (percent >= 80 ? "WARNING" : "NORMAL");
        return new TenantQuotaItemResponse(quota.getQuotaCode(), quota.getQuotaName(), max,
                quantity / unit, Math.round(percent * 10) / 10.0, status);
    }

    public void checkStorageQuota(Long tenantId, long additionalBytes) {
        if (!TenantAccessContextHolder.requiredTenantId().equals(tenantId)) {
            throw new BizException("TENANT_ACCESS_DENIED", "文件配额必须在目标租户上下文中校验");
        }
        if (additionalBytes < 0) {
            throw new IllegalArgumentException("additionalBytes must be nonnegative");
        }
        TenantQuotaEntity quota = findQuota(tenantId, "STORAGE_MB");
        if (quota == null) return;

        long maxMb = quota.getQuotaValue();
        long current = meteringMapper.sumStorageUsed(tenantId);
        BigInteger requested = BigInteger.valueOf(current).add(BigInteger.valueOf(additionalBytes));
        if (requested.compareTo(BigInteger.valueOf(maxMb).multiply(BigInteger.valueOf(BYTES_PER_MB))) > 0) {
            throw new BizException(
                    "TENANT_STORAGE_QUOTA_EXCEEDED",
                    String.format("当前租户文件存储空间已达上限 (当前使用 %.2f MB / 配额 %d MB)，无法上传新文件。请联系管理员扩容！",
                            current / (double) BYTES_PER_MB, maxMb)
            );
        }
    }

    private TenantQuotaEntity findQuota(Long tenantId, String code) {
        return quotaMapper.selectOne(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .eq(TenantQuotaEntity::getQuotaCode, code));
    }

    private void upsertQuota(Long tenantId, String code, String name, Long value,
                             Long alarmThreshold, String resetCycle, String remark) {
        TenantQuotaEntity entity = findQuota(tenantId, code);
        if (entity != null) {
            entity.setQuotaName(name);
            entity.setQuotaValue(value);
            entity.setAlarmThreshold(alarmThreshold);
            entity.setResetCycle(resetCycle);
            entity.setRemark(remark);
            quotaMapper.updateById(entity);
        } else {
            entity = new TenantQuotaEntity();
            entity.setTenantId(tenantId);
            entity.setQuotaCode(code);
            entity.setQuotaName(name);
            entity.setQuotaValue(value);
            entity.setUsedValue(0L);
            entity.setAlarmThreshold(alarmThreshold);
            entity.setResetCycle(resetCycle);
            entity.setRemark(remark);
            quotaMapper.insert(entity);
        }
    }
}
