package com.saasbasics.platform.modules.ai.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiBillingReconcileEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiRunEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiTenantQuotaEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiBillingReconcileMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiRunMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiTenantQuotaMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles AI execution run token/cost ledgers against tenant quota counters and persists auditable statements.
 */
@Service
public class AiBillingReconciliationService {

    public record ReconciliationReport(
            UUID publicId,
            String billingPeriod,
            int runCount,
            long ledgerPromptTokens,
            long ledgerCompletionTokens,
            long ledgerTotalTokens,
            BigDecimal ledgerCost,
            long quotaUsedTokens,
            BigDecimal quotaEstimatedCost,
            long tokenDiscrepancy,
            BigDecimal costDiscrepancy,
            String status,
            boolean repaired
    ) {}

    private final ObjectProvider<AiRunMapper> runMapperProvider;
    private final ObjectProvider<AiTenantQuotaMapper> quotaMapperProvider;
    private final ObjectProvider<AiBillingReconcileMapper> reconcileMapperProvider;
    private final AiMeteringQuotaService meteringQuotaService;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    public AiBillingReconciliationService(
            ObjectProvider<AiRunMapper> runMapperProvider,
            ObjectProvider<AiTenantQuotaMapper> quotaMapperProvider,
            ObjectProvider<AiBillingReconcileMapper> reconcileMapperProvider,
            AiMeteringQuotaService meteringQuotaService,
            ObjectMapper objectMapper
    ) {
        this.runMapperProvider = runMapperProvider;
        this.quotaMapperProvider = quotaMapperProvider;
        this.reconcileMapperProvider = reconcileMapperProvider;
        this.meteringQuotaService = meteringQuotaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReconciliationReport reconcile(String billingPeriod, boolean autoRepair) {
        long tenantId = TenantAccessContextHolder.requiredTenantId();
        String period = (billingPeriod == null || billingPeriod.isBlank())
                ? YearMonth.now().toString() : billingPeriod.trim();
        YearMonth ym = YearMonth.parse(period);
        var periodStart = ym.atDay(1).atStartOfDay();
        var periodEnd = ym.plusMonths(1).atDay(1).atStartOfDay();

        AiRunMapper runMapper = runMapperProvider.getIfAvailable();
        List<AiRunEntity> runs = runMapper == null ? List.of() : runMapper.selectList(
                new LambdaQueryWrapper<AiRunEntity>()
                        .eq(AiRunEntity::getTenantId, tenantId)
                        .eq(AiRunEntity::getDeleted, 0)
                        .ge(AiRunEntity::getCreatedAt, periodStart)
                        .lt(AiRunEntity::getCreatedAt, periodEnd));

        long ledgerPrompt = 0L;
        long ledgerCompletion = 0L;
        BigDecimal ledgerCost = BigDecimal.ZERO;
        Map<String, Long> tokensByModel = new LinkedHashMap<>();

        for (AiRunEntity run : runs) {
            int p = run.getPromptTokens() != null ? Math.max(0, run.getPromptTokens()) : 0;
            int c = run.getCompletionTokens() != null ? Math.max(0, run.getCompletionTokens()) : 0;
            ledgerPrompt += p;
            ledgerCompletion += c;
            BigDecimal runCost = run.getEstimatedCost() != null
                    ? run.getEstimatedCost()
                    : meteringQuotaService.estimateCost(run.getModelId(), p, c);
            ledgerCost = ledgerCost.add(runCost);
            String modelKey = run.getModelId() != null ? run.getModelId() : "unknown";
            tokensByModel.merge(modelKey, (long) (p + c), Long::sum);
        }
        long ledgerTotal = ledgerPrompt + ledgerCompletion;
        ledgerCost = ledgerCost.setScale(6, RoundingMode.HALF_UP);

        AiTenantQuotaMapper quotaMapper = quotaMapperProvider.getIfAvailable();
        AiTenantQuotaEntity quota = quotaMapper == null ? null : quotaMapper.selectOne(
                new LambdaQueryWrapper<AiTenantQuotaEntity>()
                        .eq(AiTenantQuotaEntity::getTenantId, tenantId)
                        .last("LIMIT 1"));

        long quotaTokens = quota != null && quota.getUsedTokensMonth() != null ? quota.getUsedTokensMonth() : 0L;
        BigDecimal quotaCost = quota != null && quota.getEstimatedCostMonth() != null
                ? quota.getEstimatedCostMonth().setScale(6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

        long tokenDiscrepancy = ledgerTotal - quotaTokens;
        BigDecimal costDiscrepancy = ledgerCost.subtract(quotaCost).setScale(6, RoundingMode.HALF_UP);
        boolean matched = tokenDiscrepancy == 0L && costDiscrepancy.abs().compareTo(new BigDecimal("0.000001")) <= 0;

        boolean repaired = false;
        String status = matched ? "MATCHED" : "DISCREPANCY_DETECTED";
        if (!matched && autoRepair && quota != null && quotaMapper != null) {
            quota.setPromptTokensMonth(ledgerPrompt);
            quota.setCompletionTokensMonth(ledgerCompletion);
            quota.setUsedTokensMonth(ledgerTotal);
            quota.setEstimatedCostMonth(ledgerCost);
            quotaMapper.updateById(quota);
            repaired = true;
            status = "REPAIRED";
        }

        UUID publicId = uuidGenerator.generate();
        AiBillingReconcileMapper recMapper = reconcileMapperProvider.getIfAvailable();
        if (recMapper != null) {
            AiBillingReconcileEntity entity = new AiBillingReconcileEntity();
            entity.setTenantId(tenantId);
            entity.setPublicId(publicId.toString());
            entity.setBillingPeriod(period);
            entity.setRunCount(runs.size());
            entity.setLedgerPromptTokens(ledgerPrompt);
            entity.setLedgerCompletionTokens(ledgerCompletion);
            entity.setLedgerTotalTokens(ledgerTotal);
            entity.setLedgerCost(ledgerCost);
            entity.setQuotaUsedTokens(quotaTokens);
            entity.setQuotaEstimatedCost(quotaCost);
            entity.setTokenDiscrepancy(tokenDiscrepancy);
            entity.setCostDiscrepancy(costDiscrepancy);
            entity.setStatus(status);
            entity.setSummaryJson(toJsonSafe(Map.of("tokensByModel", tokensByModel, "repaired", repaired)));
            entity.setVersion(0);
            entity.setDeleted(0);
            recMapper.insert(entity);
        }

        return new ReconciliationReport(publicId, period, runs.size(), ledgerPrompt, ledgerCompletion,
                ledgerTotal, ledgerCost, quotaTokens, quotaCost, tokenDiscrepancy, costDiscrepancy, status, repaired);
    }

    public List<ReconciliationReport> listReconciliations() {
        AiBillingReconcileMapper recMapper = reconcileMapperProvider.getIfAvailable();
        if (recMapper == null) return List.of();
        return recMapper.selectList(new LambdaQueryWrapper<AiBillingReconcileEntity>()
                        .eq(AiBillingReconcileEntity::getDeleted, 0)
                        .orderByDesc(AiBillingReconcileEntity::getId))
                .stream()
                .map(e -> new ReconciliationReport(
                        UUID.fromString(e.getPublicId()), e.getBillingPeriod(), e.getRunCount(),
                        e.getLedgerPromptTokens(), e.getLedgerCompletionTokens(), e.getLedgerTotalTokens(),
                        e.getLedgerCost(), e.getQuotaUsedTokens(), e.getQuotaEstimatedCost(),
                        e.getTokenDiscrepancy(), e.getCostDiscrepancy(), e.getStatus(),
                        "REPAIRED".equalsIgnoreCase(e.getStatus())
                ))
                .toList();
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
