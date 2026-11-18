package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.SequenceNextResponse;
import com.saasbasics.platform.modules.system.dto.SequenceRuleResponse;
import com.saasbasics.platform.modules.system.dto.SequenceRuleSaveRequest;
import com.saasbasics.platform.modules.system.entity.SequenceRuleEntity;
import com.saasbasics.platform.modules.system.mapper.SequenceRuleMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SequenceRuleService {

    private final ObjectProvider<SequenceRuleMapper> sequenceRuleMapperProvider;
    private final ObjectProvider<AuditTrailService> auditTrailServiceProvider;

    public SequenceRuleService(ObjectProvider<SequenceRuleMapper> sequenceRuleMapperProvider,
                               ObjectProvider<AuditTrailService> auditTrailServiceProvider) {
        this.sequenceRuleMapperProvider = sequenceRuleMapperProvider;
        this.auditTrailServiceProvider = auditTrailServiceProvider;
    }

    public List<SequenceRuleResponse> listRules() {
        SequenceRuleMapper mapper = requiredMapper();
        Long tenantId = currentTenantId();
        List<SequenceRuleEntity> rules = mapper.selectList(
                new LambdaQueryWrapper<SequenceRuleEntity>()
                        .eq(SequenceRuleEntity::getTenantId, tenantId)
                        .eq(SequenceRuleEntity::getDeleted, 0)
                        .orderByAsc(SequenceRuleEntity::getId)
        );
        if (rules.isEmpty() && !Objects.equals(1L, tenantId)) {
            seedFromPlatform(mapper, tenantId);
            rules = mapper.selectList(
                    new LambdaQueryWrapper<SequenceRuleEntity>()
                            .eq(SequenceRuleEntity::getTenantId, tenantId)
                            .eq(SequenceRuleEntity::getDeleted, 0)
                            .orderByAsc(SequenceRuleEntity::getId)
            );
        }
        return rules.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SequenceRuleResponse updateRule(Long id, SequenceRuleSaveRequest request) {
        SequenceRuleMapper mapper = requiredMapper();
        SequenceRuleEntity entity = mapper.selectById(id);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new BizException("SEQUENCE_RULE_NOT_FOUND", "序列规则不存在: " + id);
        }
        entity.setRuleName(request.name().trim());
        entity.setPrefix(request.prefix() != null ? request.prefix().trim() : entity.getPrefix());
        if (request.datePattern() != null) {
            entity.setDatePattern(request.datePattern().trim());
        }
        if (request.seqLength() != null) {
            entity.setSeqLength(Math.max(2, Math.min(18, request.seqLength())));
        }
        if (request.step() != null) {
            entity.setStep(Math.max(1, request.step()));
        }
        if (request.resetCycle() != null && !request.resetCycle().isBlank()) {
            entity.setResetCycle(request.resetCycle().trim().toUpperCase(Locale.ROOT));
        }
        if (request.status() != null && !request.status().isBlank()) {
            entity.setStatus(request.status().trim().toUpperCase(Locale.ROOT));
        }
        mapper.updateById(entity);
        recordAudit("sys_sequence_rule", String.valueOf(entity.getId()), "UPDATE", entity.getRuleCode(), entity.getRuleName());
        return toResponse(entity);
    }

    @Transactional
    public SequenceNextResponse nextValue(String code) {
        SequenceRuleMapper mapper = requiredMapper();
        SequenceRuleEntity entity = findByCodeOrThrow(mapper, code);
        int step = entity.getStep() != null && entity.getStep() > 0 ? entity.getStep() : 1;
        long current = entity.getCurrentValue() != null ? entity.getCurrentValue() : 0L;
        long next = current + step;
        entity.setCurrentValue(next);
        int updated = mapper.updateById(entity);
        if (updated == 0) {
            entity = findByCodeOrThrow(mapper, code);
            current = entity.getCurrentValue() != null ? entity.getCurrentValue() : 0L;
            next = current + step;
            entity.setCurrentValue(next);
            mapper.updateById(entity);
        }
        String formatted = formatSequence(
                entity.getPrefix(),
                entity.getDatePattern(),
                entity.getSeqLength() != null ? entity.getSeqLength() : 6,
                next
        );
        recordAudit("sys_sequence_rule", String.valueOf(entity.getId()), "NEXT", entity.getRuleCode(), formatted);
        return new SequenceNextResponse(entity.getRuleCode(), next, formatted);
    }

    @Transactional
    public SequenceRuleResponse resetRule(String code) {
        SequenceRuleMapper mapper = requiredMapper();
        SequenceRuleEntity entity = findByCodeOrThrow(mapper, code);
        Long prev = entity.getCurrentValue();
        entity.setCurrentValue(0L);
        mapper.updateById(entity);
        recordAudit("sys_sequence_rule", String.valueOf(entity.getId()), "RESET", String.valueOf(prev), "0");
        return toResponse(entity);
    }

    public static String formatSequence(String prefix, String datePattern, int seqLength, long value) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append(prefix.trim());
        }
        if (datePattern != null && !datePattern.isBlank()) {
            String javaPattern = normalizeDatePattern(datePattern);
            try {
                String datePart = DateTimeFormatter.ofPattern(javaPattern).format(LocalDateTime.now());
                if (!sb.isEmpty()) {
                    sb.append('_');
                }
                sb.append(datePart);
            } catch (Exception ignored) {
                if (!sb.isEmpty()) {
                    sb.append('_');
                }
                sb.append(DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()));
            }
        }
        int padLen = Math.max(2, Math.min(18, seqLength));
        String padded = String.format(Locale.ROOT, "%0" + padLen + "d", Math.max(0L, value));
        if (!sb.isEmpty()) {
            sb.append('_');
        }
        sb.append(padded);
        return sb.toString();
    }

    private static String normalizeDatePattern(String raw) {
        String p = raw.trim();
        return p.replace("YYYY", "yyyy").replace("DD", "dd");
    }

    private SequenceRuleResponse toResponse(SequenceRuleEntity entity) {
        int len = entity.getSeqLength() != null ? entity.getSeqLength() : 6;
        int step = entity.getStep() != null ? entity.getStep() : 1;
        long seq = entity.getCurrentValue() != null ? entity.getCurrentValue() : 0L;
        String datePattern = entity.getDatePattern() != null ? entity.getDatePattern() : "yyyyMMdd";
        String displayPattern = buildDisplayPattern(entity.getPrefix(), datePattern, len);
        String sample = formatSequence(entity.getPrefix(), datePattern, len, seq);
        return new SequenceRuleResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getPrefix(),
                datePattern,
                displayPattern,
                len,
                step,
                seq,
                entity.getResetCycle() != null ? entity.getResetCycle() : "DAILY",
                sample,
                entity.getStatus() != null ? entity.getStatus() : "ENABLED"
        );
    }

    private String buildDisplayPattern(String prefix, String datePattern, int seqLength) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append("{PREFIX}");
        }
        if (datePattern != null && !datePattern.isBlank()) {
            if (!sb.isEmpty()) sb.append('_');
            sb.append('{').append(datePattern.toUpperCase(Locale.ROOT)).append('}');
        }
        if (!sb.isEmpty()) sb.append('_');
        sb.append("{SEQ:").append(seqLength).append('}');
        return sb.toString();
    }

    private SequenceRuleEntity findByCodeOrThrow(SequenceRuleMapper mapper, String code) {
        Long tenantId = currentTenantId();
        String trimmedCode = code != null ? code.trim() : "";
        SequenceRuleEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<SequenceRuleEntity>()
                        .eq(SequenceRuleEntity::getTenantId, tenantId)
                        .eq(SequenceRuleEntity::getRuleCode, trimmedCode)
                        .eq(SequenceRuleEntity::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (entity == null && tenantId > 1L) {
            entity = seedSingleRuleFromPlatform(mapper, tenantId, trimmedCode);
        }
        if (entity == null) {
            throw new BizException("SEQUENCE_RULE_NOT_FOUND", "序列规则不存在: " + code);
        }
        return entity;
    }

    private SequenceRuleEntity seedSingleRuleFromPlatform(SequenceRuleMapper mapper, Long tenantId, String ruleCode) {
        SequenceRuleEntity src;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Fallback clone sequence rule from tenant 1")) {
            src = mapper.selectOne(
                    new LambdaQueryWrapper<SequenceRuleEntity>()
                            .eq(SequenceRuleEntity::getTenantId, 1L)
                            .eq(SequenceRuleEntity::getRuleCode, ruleCode)
                            .eq(SequenceRuleEntity::getDeleted, 0)
                            .last("LIMIT 1")
            );
        }
        if (src == null) {
            return null;
        }
        SequenceRuleEntity copy = copyFromPlatform(src, tenantId);
        mapper.insert(copy);
        return copy;
    }

    private void seedFromPlatform(SequenceRuleMapper mapper, Long tenantId) {
        List<SequenceRuleEntity> defaults;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Clone sequence rules from tenant 1")) {
            defaults = mapper.selectList(
                    new LambdaQueryWrapper<SequenceRuleEntity>()
                            .eq(SequenceRuleEntity::getTenantId, 1L)
                            .eq(SequenceRuleEntity::getDeleted, 0)
            );
        }
        for (SequenceRuleEntity src : defaults) {
            mapper.insert(copyFromPlatform(src, tenantId));
        }
    }

    private SequenceRuleEntity copyFromPlatform(SequenceRuleEntity src, Long tenantId) {
        SequenceRuleEntity copy = new SequenceRuleEntity();
        copy.setTenantId(tenantId);
        copy.setRuleCode(src.getRuleCode());
        copy.setRuleName(src.getRuleName());
        copy.setPrefix(src.getPrefix());
        copy.setDatePattern(src.getDatePattern());
        copy.setSeqLength(src.getSeqLength());
        copy.setStep(src.getStep());
        copy.setCurrentValue(0L);
        copy.setResetCycle(src.getResetCycle());
        copy.setStatus(src.getStatus());
        copy.setRemark(src.getRemark());
        return copy;
    }

    private Long currentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal != null && principal.tenantId() != null) {
            return principal.tenantId();
        }
        TenantAccessContext ctx = TenantAccessContextHolder.current();
        if (ctx != null && ctx.tenantId() != null) {
            return ctx.tenantId();
        }
        return 1L;
    }

    private void recordAudit(String bizType, String bizId, String opType, String reqBody, String respBody) {
        AuditTrailService trail = auditTrailServiceProvider != null ? auditTrailServiceProvider.getIfAvailable() : null;
        if (trail != null) {
            trail.record("system", bizType, bizId, opType, reqBody, respBody, true);
        }
    }

    private SequenceRuleMapper requiredMapper() {
        SequenceRuleMapper mapper = sequenceRuleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "序列规则服务需要数据库连接");
        }
        return mapper;
    }
}
