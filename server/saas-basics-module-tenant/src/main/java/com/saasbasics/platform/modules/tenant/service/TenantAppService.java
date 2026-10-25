package com.saasbasics.platform.modules.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantFeatureProvider;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.tenant.dto.TenantAppCatalogItemResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppResponse;
import com.saasbasics.platform.modules.tenant.dto.TenantAppSubscribeRequest;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import com.saasbasics.platform.modules.tenant.mapper.TenantAppMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAppService {

    public record SystemAppMeta(
            String appCode,
            String appName,
            String description,
            String category,
            String icon,
            String requiredTier
    ) {}

    public static final List<SystemAppMeta> SYSTEM_APPS = List.of(
            new SystemAppMeta("iam", "统一身份与权限中心 (IAM)", "用户身份认证、企业组织架构、RBAC角色授权、细粒度数据权限与多因素安全策略", "CORE", "UserFilled", "BASIC"),
            new SystemAppMeta("tenant", "多租户与计量治理中心", "企业租户空间开通、多租户数据隔离模式、资源配额限制、账单台账与灾备快照还原", "CORE", "OfficeBuilding", "BASIC"),
            new SystemAppMeta("workflow", "企业可视化审批流 (BPMN)", "可视化审批流设计器、多级表单动态流转、条件分支网关、并签汇签与任务督办通知", "COLLABORATION", "Share", "PROFESSIONAL"),
            new SystemAppMeta("file", "分布式文件存储中心", "多驱动对象存储适配、分片断点续传、文件防篡改哈希校验与生命周期冷热归档", "INFRA", "FolderOpened", "PROFESSIONAL"),
            new SystemAppMeta("crm", "客户关系管理 (CRM)", "销售线索跟进、公海池自动回收、商机阶段漏斗、商务合同管控与财务回款结算", "BUSINESS", "TrendCharts", "ENTERPRISE"),
            new SystemAppMeta("erp", "进销存 ERP 供应链", "商品物料档案、多仓实时库存台账、出入库动态溯源与采购订单全流程履约", "BUSINESS", "Briefcase", "ENTERPRISE"),
            new SystemAppMeta("scheduler", "分布式任务调度中心", "高可用分布式定时任务编排、Cron灵活触发、故障重试转移与多通道即时告警", "INFRA", "Timer", "ENTERPRISE"),
            new SystemAppMeta("audit", "合规审计与风控中心", "全系统用户操作行为日志留存、敏感数据动态脱敏追踪与合规审计报表导出", "SECURITY", "DataAnalysis", "ENTERPRISE"),
            new SystemAppMeta("integration", "开放集成与智能中心", "多源异构数据集成通道、企业网关开放平台、AI 智能助手与消息通知网关", "INTEGRATION", "Connection", "ENTERPRISE"),
            new SystemAppMeta("system", "系统中心与企业 SSO", "开放平台 AppKey/Secret 接入凭证、企业级单点登录 (OIDC/OAuth2/SAML) 与全局参数配置", "SYSTEM", "Setting", "ENTERPRISE"),
            new SystemAppMeta("codegen", "低代码与代码生成中心", "基于数据库元数据的端到端代码一键生成引擎与前后端工程可视化模板", "DEV", "MagicStick", "PLATFORM")
    );

    private final ObjectProvider<TenantAppMapper> tenantAppMapperProvider;
    private final TenantFeatureProvider tenantFeatureProvider;
    private final AuditTrailService auditTrailService;

    public TenantAppService(ObjectProvider<TenantAppMapper> tenantAppMapperProvider,
                            TenantFeatureProvider tenantFeatureProvider,
                            AuditTrailService auditTrailService) {
        this.tenantAppMapperProvider = tenantAppMapperProvider;
        this.tenantFeatureProvider = tenantFeatureProvider;
        this.auditTrailService = auditTrailService;
    }

    public List<TenantAppCatalogItemResponse> listAppCatalog(Long tenantId) {
        Set<String> effectiveFeatures = tenantFeatureProvider.getEffectiveFeatures(tenantId);
        boolean isAllAccess = effectiveFeatures.contains("*") || (tenantId != null && tenantId <= 0L);

        Map<String, TenantAppEntity> subscribedAppsMap = Map.of();
        TenantAppMapper appMapper = tenantAppMapperProvider.getIfAvailable();
        if (appMapper != null && tenantId != null) {
            List<TenantAppEntity> list = appMapper.selectList(new LambdaQueryWrapper<TenantAppEntity>()
                    .eq(TenantAppEntity::getTenantId, tenantId)
                    .eq(TenantAppEntity::getDeleted, 0));
            subscribedAppsMap = list.stream().collect(
                    java.util.stream.Collectors.toMap(
                            item -> item.getAppCode().toLowerCase(Locale.ROOT),
                            item -> item,
                            (first, second) -> first
                    )
            );
        }

        LocalDateTime now = LocalDateTime.now();
        List<TenantAppCatalogItemResponse> results = new ArrayList<>();

        for (SystemAppMeta meta : SYSTEM_APPS) {
            String code = meta.appCode().toLowerCase(Locale.ROOT);
            TenantAppEntity subscription = subscribedAppsMap.get(code);

            boolean isSubscribed = subscription != null;
            boolean isExplicitlyDisabled = isSubscribed && "DISABLED".equalsIgnoreCase(subscription.getStatus());
            boolean isExpired = isSubscribed && subscription.getExpiredAt() != null && subscription.getExpiredAt().isBefore(now);
            boolean isTrial = isSubscribed && subscription.getExpiredAt() != null && subscription.getExpiredAt().isAfter(now);

            boolean hasAccess = (!isExplicitlyDisabled && !isExpired && (
                    isAllAccess || isSubscribed || tenantFeatureProvider.hasFeature(tenantId, code)
            ));

            String status;
            if (hasAccess) {
                status = isTrial ? "TRIAL" : "ENABLED";
            } else if (isExpired) {
                status = "EXPIRED";
            } else {
                status = "DISABLED";
            }

            Long daysRemaining = null;
            if (subscription != null && subscription.getExpiredAt() != null) {
                Duration duration = Duration.between(now, subscription.getExpiredAt());
                daysRemaining = Math.max(0, duration.toDays());
            }

            boolean isIncludedInPlan = isAllAccess || tenantFeatureProvider.hasFeature(tenantId, code);

            results.add(new TenantAppCatalogItemResponse(
                    meta.appCode(),
                    meta.appName(),
                    meta.description(),
                    meta.category(),
                    meta.icon(),
                    meta.requiredTier(),
                    status,
                    subscription != null ? subscription.getOpenedAt() : null,
                    subscription != null ? subscription.getExpiredAt() : null,
                    daysRemaining,
                    isTrial,
                    isIncludedInPlan,
                    !hasAccess || isTrial
            ));
        }

        return results;
    }

    @Transactional
    public TenantAppResponse subscribeApp(Long tenantId, TenantAppSubscribeRequest request) {
        TenantAppMapper appMapper = tenantAppMapperProvider.getIfAvailable();
        if (appMapper == null) {
            throw new BizException("SYSTEM_ERROR", "TenantAppMapper not available");
        }

        String appCode = request.appCode().trim().toLowerCase(Locale.ROOT);
        SystemAppMeta meta = SYSTEM_APPS.stream()
                .filter(m -> m.appCode().equalsIgnoreCase(appCode))
                .findFirst()
                .orElseThrow(() -> new BizException("APP_NOT_FOUND", "Unknown appCode: " + appCode));

        TenantAppEntity entity = appMapper.selectOne(new LambdaQueryWrapper<TenantAppEntity>()
                .eq(TenantAppEntity::getTenantId, tenantId)
                .eq(TenantAppEntity::getAppCode, appCode)
                .eq(TenantAppEntity::getDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = (request.trialDays() != null && request.trialDays() > 0)
                ? now.plusDays(request.trialDays())
                : null;

        if (entity == null) {
            entity = new TenantAppEntity();
            entity.setTenantId(tenantId);
            entity.setAppCode(appCode);
            entity.setAppName(meta.appName());
            entity.setStatus("ENABLED");
            entity.setOpenedAt(now);
            entity.setExpiredAt(expireAt);
            entity.setRemark(request.remark());
            appMapper.insert(entity);
        } else {
            entity.setStatus("ENABLED");
            entity.setOpenedAt(now);
            entity.setExpiredAt(expireAt);
            if (request.remark() != null) {
                entity.setRemark(request.remark());
            }
            appMapper.updateById(entity);
        }

        tenantFeatureProvider.invalidateCache(tenantId);

        auditTrailService.record(
                "tenant",
                "plat_tenant_app",
                String.valueOf(tenantId),
                "SUBSCRIBE_APP",
                appCode,
                expireAt != null ? ("Trial until " + expireAt) : "Permanent",
                true
        );

        return new TenantAppResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getAppCode(),
                entity.getAppName(),
                entity.getStatus(),
                entity.getOpenedAt(),
                entity.getExpiredAt(),
                entity.getConfigJson(),
                entity.getRemark()
        );
    }

    @Transactional
    public void updateAppStatus(Long tenantId, Long appId, String status) {
        TenantAppMapper appMapper = tenantAppMapperProvider.getIfAvailable();
        if (appMapper == null) {
            throw new BizException("SYSTEM_ERROR", "TenantAppMapper not available");
        }

        TenantAppEntity entity = appMapper.selectById(appId);
        if (entity == null || !Objects.equals(entity.getTenantId(), tenantId) || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new BizException("APP_SUBSCRIPTION_NOT_FOUND", "App subscription not found");
        }

        entity.setStatus(status);
        appMapper.updateById(entity);

        tenantFeatureProvider.invalidateCache(tenantId);

        auditTrailService.record(
                "tenant",
                "plat_tenant_app",
                String.valueOf(tenantId),
                "UPDATE_APP_STATUS",
                entity.getAppCode(),
                status,
                true
        );
    }

    @Transactional
    public void toggleAppAuthorization(Long tenantId, String appCode, boolean enabled, String remark) {
        TenantAppMapper appMapper = tenantAppMapperProvider.getIfAvailable();
        if (appMapper == null) {
            throw new BizException("SYSTEM_ERROR", "TenantAppMapper not available");
        }

        String targetCode = appCode.trim().toLowerCase(Locale.ROOT);
        SystemAppMeta meta = SYSTEM_APPS.stream()
                .filter(m -> m.appCode().equalsIgnoreCase(targetCode))
                .findFirst()
                .orElseThrow(() -> new BizException("APP_NOT_FOUND", "Unknown appCode: " + targetCode));

        TenantAppEntity entity = appMapper.selectOne(new LambdaQueryWrapper<TenantAppEntity>()
                .eq(TenantAppEntity::getTenantId, tenantId)
                .eq(TenantAppEntity::getAppCode, targetCode)
                .eq(TenantAppEntity::getDeleted, 0));

        LocalDateTime now = LocalDateTime.now();
        String newStatus = enabled ? "ENABLED" : "DISABLED";

        if (entity == null) {
            entity = new TenantAppEntity();
            entity.setTenantId(tenantId);
            entity.setAppCode(targetCode);
            entity.setAppName(meta.appName());
            entity.setStatus(newStatus);
            entity.setOpenedAt(now);
            entity.setExpiredAt(null);
            entity.setRemark(remark != null ? remark : (enabled ? "管理员授权应用权限" : "管理员回收应用权限"));
            appMapper.insert(entity);
        } else {
            entity.setStatus(newStatus);
            if (enabled && entity.getOpenedAt() == null) {
                entity.setOpenedAt(now);
            }
            if (remark != null) {
                entity.setRemark(remark);
            }
            appMapper.updateById(entity);
        }

        tenantFeatureProvider.invalidateCache(tenantId);

        auditTrailService.record(
                "tenant",
                "plat_tenant_app",
                String.valueOf(tenantId),
                "TOGGLE_APP_AUTHORIZATION",
                targetCode,
                newStatus,
                true
        );
    }
}
