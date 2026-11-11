package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.application.dto.AiTenantQuotaDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiTenantQuotaSaveRequest;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiTenantQuotaEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiTenantQuotaMapper;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户 AI Token 计量核算、预算熔断配额与 Prometheus 指标打点服务。
 */
@Service
public class AiMeteringQuotaService {

    private static final long DEFAULT_MONTHLY_LIMIT = 5_000_000L;
    private static final int DEFAULT_RPM_LIMIT = 60;

    private final AiTenantQuotaMapper quotaMapper;
    private final ObjectProvider<PlatformTelemetryService> telemetryServiceProvider;

    public AiMeteringQuotaService(
            AiTenantQuotaMapper quotaMapper,
            ObjectProvider<PlatformTelemetryService> telemetryServiceProvider
    ) {
        this.quotaMapper = quotaMapper;
        this.telemetryServiceProvider = telemetryServiceProvider;
    }

    public AiTenantQuotaDTO getCurrentQuota() {
        AiTenantQuotaEntity entity = getOrCreateQuotaEntity(currentTenantId());
        return toDto(entity);
    }

    @Transactional
    public AiTenantQuotaDTO updateQuota(AiTenantQuotaSaveRequest request) {
        Long tenantId = currentTenantId();
        AiTenantQuotaEntity entity = getOrCreateQuotaEntity(tenantId);
        if (request.monthlyTokenLimit() != null && request.monthlyTokenLimit() > 0) {
            entity.setMonthlyTokenLimit(request.monthlyTokenLimit());
        }
        if (request.rpmLimit() != null && request.rpmLimit() > 0) {
            entity.setRpmLimit(request.rpmLimit());
        }
        quotaMapper.updateById(entity);
        return toDto(entity);
    }

    public void checkQuota(Long tenantId) {
        long targetTenant = tenantId != null ? tenantId : currentTenantId();
        AiTenantQuotaEntity quota = quotaMapper.selectOne(
                new LambdaQueryWrapper<AiTenantQuotaEntity>()
                        .eq(AiTenantQuotaEntity::getTenantId, targetTenant)
                        .last("LIMIT 1")
        );
        if (quota == null) {
            return;
        }
        if ("DISABLED".equalsIgnoreCase(quota.getStatus())) {
            throw new BizException("AI_QUOTA_EXCEEDED", "当前租户 AI 智能网关权限已被暂停，请联系平台管理员");
        }
        long limit = quota.getMonthlyTokenLimit() != null ? quota.getMonthlyTokenLimit() : DEFAULT_MONTHLY_LIMIT;
        long used = quota.getUsedTokensMonth() != null ? quota.getUsedTokensMonth() : 0L;
        if (used >= limit) {
            throw new BizException(
                    "AI_QUOTA_EXCEEDED",
                    "当前租户本月 AI Token 预算已耗尽（" + used + " / " + limit + "），请提升配额后继续"
            );
        }
    }

    @Transactional
    public BigDecimal recordUsage(
            Long tenantId,
            String providerCode,
            String modelId,
            String status,
            int promptTokens,
            int completionTokens,
            long latencyMs
    ) {
        int safePrompt = Math.max(0, promptTokens);
        int safeCompletion = Math.max(0, completionTokens);
        int deltaTokens = safePrompt + safeCompletion;
        BigDecimal deltaCost = estimateCost(modelId, safePrompt, safeCompletion);

        long targetTenant = tenantId != null ? tenantId : currentTenantId();
        AiTenantQuotaEntity quota = getOrCreateQuotaEntity(targetTenant);
        quota.setPromptTokensMonth(safeLong(quota.getPromptTokensMonth()) + safePrompt);
        quota.setCompletionTokensMonth(safeLong(quota.getCompletionTokensMonth()) + safeCompletion);
        quota.setUsedTokensMonth(safeLong(quota.getUsedTokensMonth()) + deltaTokens);
        BigDecimal prevCost = quota.getEstimatedCostMonth() != null ? quota.getEstimatedCostMonth() : BigDecimal.ZERO;
        quota.setEstimatedCostMonth(prevCost.add(deltaCost));
        quotaMapper.updateById(quota);

        PlatformTelemetryService telemetry = telemetryServiceProvider.getIfAvailable();
        if (telemetry != null) {
            telemetry.recordAiUsage(providerCode, modelId, status, safePrompt, safeCompletion, latencyMs);
        }
        return deltaCost;
    }

    public BigDecimal estimateCost(String modelId, int promptTokens, int completionTokens) {
        // Blended conservative estimation per 1M tokens in RMB/USD units for governance budgeting
        double inputRatePer1M = 2.0;
        double outputRatePer1M = 8.0;
        if (modelId != null) {
            String lower = modelId.toLowerCase();
            if (lower.contains("gpt-4o") && !lower.contains("mini")) {
                inputRatePer1M = 18.0;
                outputRatePer1M = 72.0;
            } else if (lower.contains("ollama") || lower.contains("llama")) {
                inputRatePer1M = 0.0;
                outputRatePer1M = 0.0;
            }
        }
        double cost = (promptTokens / 1_000_000.0) * inputRatePer1M + (completionTokens / 1_000_000.0) * outputRatePer1M;
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    private AiTenantQuotaEntity getOrCreateQuotaEntity(Long tenantId) {
        String currentMonth = YearMonth.now().toString();
        AiTenantQuotaEntity existing = quotaMapper.selectOne(
                new LambdaQueryWrapper<AiTenantQuotaEntity>()
                        .eq(AiTenantQuotaEntity::getTenantId, tenantId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            if (!currentMonth.equals(existing.getBillingMonth())) {
                existing.setBillingMonth(currentMonth);
                existing.setUsedTokensMonth(0L);
                existing.setPromptTokensMonth(0L);
                existing.setCompletionTokensMonth(0L);
                existing.setEstimatedCostMonth(BigDecimal.ZERO);
                quotaMapper.updateById(existing);
            }
            return existing;
        }
        AiTenantQuotaEntity created = new AiTenantQuotaEntity();
        created.setTenantId(tenantId);
        created.setMonthlyTokenLimit(DEFAULT_MONTHLY_LIMIT);
        created.setUsedTokensMonth(0L);
        created.setPromptTokensMonth(0L);
        created.setCompletionTokensMonth(0L);
        created.setEstimatedCostMonth(BigDecimal.ZERO);
        created.setBillingMonth(currentMonth);
        created.setRpmLimit(DEFAULT_RPM_LIMIT);
        created.setStatus("ENABLED");
        created.setRemark("自动初始化租户默认 AI 预算配额");
        quotaMapper.insert(created);
        return created;
    }

    private AiTenantQuotaDTO toDto(AiTenantQuotaEntity e) {
        long limit = e.getMonthlyTokenLimit() != null ? e.getMonthlyTokenLimit() : DEFAULT_MONTHLY_LIMIT;
        long used = safeLong(e.getUsedTokensMonth());
        long remaining = Math.max(0L, limit - used);
        double pct = limit > 0 ? Math.min(100.0, Math.round((used * 10000.0) / limit) / 100.0) : 0.0;
        return new AiTenantQuotaDTO(
                e.getTenantId(),
                limit,
                used,
                safeLong(e.getPromptTokensMonth()),
                safeLong(e.getCompletionTokensMonth()),
                remaining,
                pct,
                e.getEstimatedCostMonth() != null ? e.getEstimatedCostMonth() : BigDecimal.ZERO,
                e.getBillingMonth(),
                e.getRpmLimit() != null ? e.getRpmLimit() : DEFAULT_RPM_LIMIT,
                e.getStatus()
        );
    }

    private long safeLong(Long val) {
        return val != null ? val : 0L;
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
