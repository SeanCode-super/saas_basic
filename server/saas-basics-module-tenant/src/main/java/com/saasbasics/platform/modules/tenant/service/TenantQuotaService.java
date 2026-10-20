package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaItemResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantQuotaOverviewResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantPackageMapper;
import com.saasbasics.platform.modules.tenant.mapper.TenantQuotaMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQuotaService {

    private final TenantQuotaMapper quotaMapper;
    private final TenantPackageMapper packageMapper;
    private final TenantMapper tenantMapper;
    private final JdbcTemplate jdbcTemplate;

    public TenantQuotaService(TenantQuotaMapper quotaMapper,
                              TenantPackageMapper packageMapper,
                              TenantMapper tenantMapper,
                              JdbcTemplate jdbcTemplate) {
        this.quotaMapper = quotaMapper;
        this.packageMapper = packageMapper;
        this.tenantMapper = tenantMapper;
        this.jdbcTemplate = jdbcTemplate;
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
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException("TENANT_NOT_FOUND", "租户不存在");
        }
        TenantPackageEntity pkg = packageMapper.selectById(tenant.getPackageId());
        String packageName = pkg != null ? pkg.getPackageName() : "未分配套餐";

        // Query real-time active users and depts
        Integer liveUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_user WHERE tenant_id = ? AND deleted = 0",
                Integer.class, tenantId);
        Integer liveDeptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_department WHERE tenant_id = ? AND deleted = 0",
                Integer.class, tenantId);

        List<TenantQuotaEntity> quotaEntities = quotaMapper.selectByTenantId(tenantId);
        List<TenantQuotaItemResponse> items = new ArrayList<>();

        for (TenantQuotaEntity q : quotaEntities) {
            long used = q.getUsedValue() != null ? q.getUsedValue() : 0L;
            if ("USER_COUNT".equalsIgnoreCase(q.getQuotaCode()) && liveUserCount != null) {
                used = liveUserCount;
            } else if ("DEPT_COUNT".equalsIgnoreCase(q.getQuotaCode()) && liveDeptCount != null) {
                used = liveDeptCount;
            }

            long max = q.getQuotaValue() != null && q.getQuotaValue() > 0 ? q.getQuotaValue() : 1L;
            double pct = Math.min(100.0, Math.round(((double) used / max) * 1000.0) / 10.0);
            String status = pct >= 100.0 ? "DANGER" : (pct >= 80.0 ? "WARNING" : "NORMAL");

            items.add(new TenantQuotaItemResponse(
                    q.getQuotaCode(),
                    q.getQuotaName(),
                    max,
                    used,
                    pct,
                    status
            ));
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
                expireAt != null ? expireAt.toString().replace("T", " ").substring(0, 19) : "永久有效",
                daysRemaining,
                lifecycleStatus,
                items
        );
    }

    public void checkUserQuota(Long tenantId) {
        if (tenantId == null || tenantId <= 0) return;
        List<TenantQuotaEntity> list = quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .eq(TenantQuotaEntity::getQuotaCode, "USER_COUNT")
                .last("LIMIT 1"));
        if (list.isEmpty()) return;

        TenantQuotaEntity quota = list.get(0);
        long max = quota.getQuotaValue() != null ? quota.getQuotaValue() : Long.MAX_VALUE;

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_user WHERE tenant_id = ? AND deleted = 0",
                Integer.class, tenantId);
        long current = count != null ? count : 0L;

        if (current >= max) {
            throw new BizException(
                    "TENANT_USER_QUOTA_EXCEEDED",
                    String.format("当前租户账号席位已达到套餐配额上限 (%d/%d)，无法继续创建新成员。请联系平台管理员升级套餐！", current, max)
            );
        }
    }

    public void checkDeptQuota(Long tenantId) {
        if (tenantId == null || tenantId <= 0) return;
        List<TenantQuotaEntity> list = quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .eq(TenantQuotaEntity::getQuotaCode, "DEPT_COUNT")
                .last("LIMIT 1"));
        if (list.isEmpty()) return;

        TenantQuotaEntity quota = list.get(0);
        long max = quota.getQuotaValue() != null ? quota.getQuotaValue() : Long.MAX_VALUE;

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_department WHERE tenant_id = ? AND deleted = 0",
                Integer.class, tenantId);
        long current = count != null ? count : 0L;

        if (current >= max) {
            throw new BizException(
                    "TENANT_DEPT_QUOTA_EXCEEDED",
                    String.format("当前租户部门数量已达到套餐配额上限 (%d/%d)，无法继续创建新部门。请升级套餐！", current, max)
            );
        }
    }

    public void checkStorageQuota(Long tenantId, long additionalBytes) {
        if (tenantId == null || tenantId <= 0) return;
        List<TenantQuotaEntity> list = quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .eq(TenantQuotaEntity::getQuotaCode, "STORAGE_MB")
                .last("LIMIT 1"));
        if (list.isEmpty()) return;

        TenantQuotaEntity quota = list.get(0);
        long maxMb = quota.getQuotaValue() != null ? quota.getQuotaValue() : Long.MAX_VALUE;
        long maxBytes = maxMb * 1024L * 1024L;

        Long currentBytes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(file_size), 0) FROM sys_file WHERE tenant_id = ? AND deleted = 0",
                Long.class, tenantId);
        long current = currentBytes != null ? currentBytes : 0L;

        if (current + additionalBytes > maxBytes) {
            throw new BizException(
                    "TENANT_STORAGE_QUOTA_EXCEEDED",
                    String.format("当前租户文件存储空间已达上限 (当前使用 %.2f MB / 配额 %d MB)，无法上传新文件。请联系管理员扩容！",
                            current / (1024.0 * 1024.0), maxMb)
            );
        }
    }


    private void upsertQuota(Long tenantId, String code, String name, Long value,
                             Long alarmThreshold, String resetCycle, String remark) {
        List<TenantQuotaEntity> existing = quotaMapper.selectList(new LambdaQueryWrapper<TenantQuotaEntity>()
                .eq(TenantQuotaEntity::getTenantId, tenantId)
                .eq(TenantQuotaEntity::getQuotaCode, code));

        if (!existing.isEmpty()) {
            TenantQuotaEntity entity = existing.get(0);
            entity.setQuotaName(name);
            entity.setQuotaValue(value);
            entity.setAlarmThreshold(alarmThreshold);
            entity.setResetCycle(resetCycle);
            entity.setRemark(remark);
            quotaMapper.updateById(entity);
        } else {
            TenantQuotaEntity entity = new TenantQuotaEntity();
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
