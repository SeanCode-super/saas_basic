package com.saasbasics.platform.modules.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.security.SecurityBlacklistChecker;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.dto.AuditOverviewResponse;
import com.saasbasics.platform.modules.audit.dto.BlacklistCreateRequest;
import com.saasbasics.platform.modules.audit.dto.BlacklistItemResponse;
import com.saasbasics.platform.modules.audit.dto.HighRiskActionItemResponse;
import com.saasbasics.platform.modules.audit.dto.LoginLogResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogDiffResponse.FieldDiffItem;
import com.saasbasics.platform.modules.audit.dto.OperationLogResponse;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateRequest;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateResponse;
import com.saasbasics.platform.modules.audit.dto.RiskRuleResponse;
import com.saasbasics.platform.modules.audit.entity.LoginLogEntity;
import com.saasbasics.platform.modules.audit.entity.OperationLogEntity;
import com.saasbasics.platform.modules.audit.entity.RiskRuleEntity;
import com.saasbasics.platform.modules.audit.entity.SecBlacklistEntity;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import com.saasbasics.platform.modules.audit.mapper.SecBlacklistMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService implements SecurityBlacklistChecker {

    private static final Set<String> VALID_BLACKLIST_TYPES = Set.of("IP", "USER", "DEVICE", "TENANT");
    private static final long BLACKLIST_CACHE_TTL_MS = 5000L;

    private final ObjectProvider<LoginLogMapper> loginLogMapperProvider;
    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectProvider<RiskRuleMapper> riskRuleMapperProvider;
    private final ObjectProvider<SecBlacklistMapper> blacklistMapperProvider;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectProvider<AuditTrailService> auditTrailServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile CachedBlacklistSnapshot blacklistSnapshot;

    private record CachedBlacklistRow(Long tenantId, String blackType, String blackValue, String reason) {}

    private record CachedBlacklistSnapshot(long expiresAtMs, boolean enabled, List<CachedBlacklistRow> rows) {}

    public AuditService(ObjectProvider<LoginLogMapper> loginLogMapperProvider,
                        ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                        ObjectProvider<RiskRuleMapper> riskRuleMapperProvider) {
        this(loginLogMapperProvider, operationLogMapperProvider, riskRuleMapperProvider, null, null, null);
    }

    @Autowired
    public AuditService(ObjectProvider<LoginLogMapper> loginLogMapperProvider,
                        ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                        ObjectProvider<RiskRuleMapper> riskRuleMapperProvider,
                        ObjectProvider<SecBlacklistMapper> blacklistMapperProvider,
                        ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                        ObjectProvider<AuditTrailService> auditTrailServiceProvider) {
        this.loginLogMapperProvider = loginLogMapperProvider;
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.riskRuleMapperProvider = riskRuleMapperProvider;
        this.blacklistMapperProvider = blacklistMapperProvider;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.auditTrailServiceProvider = auditTrailServiceProvider;
    }

    public AuditOverviewResponse overview() {
        LoginLogMapper loginLogMapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        OperationLogMapper operationLogMapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        RiskRuleMapper riskRuleMapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");

        long loginCount = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLogEntity>());
        long operationCount = operationLogMapper.selectCount(new LambdaQueryWrapper<OperationLogEntity>());
        long riskRuleCount = riskRuleMapper.selectCount(new LambdaQueryWrapper<RiskRuleEntity>().eq(RiskRuleEntity::getDeleted, 0));
        long blacklistCount = 0L;
        SecBlacklistMapper blacklistMapper = blacklistMapperProvider != null ? blacklistMapperProvider.getIfAvailable() : null;
        if (blacklistMapper != null) {
            blacklistCount = blacklistMapper.selectCount(
                    new LambdaQueryWrapper<SecBlacklistEntity>()
                            .eq(SecBlacklistEntity::getDeleted, 0)
                            .eq(SecBlacklistEntity::getStatus, "ENABLED")
            );
        }
        return new AuditOverviewResponse(loginCount, operationCount, blacklistCount, riskRuleCount);
    }

    public List<LoginLogResponse> listLoginLogs(String username, Boolean success) {
        LoginLogMapper mapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .orderByDesc(LoginLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (username != null && !username.isBlank()) {
            wrapper.like(LoginLogEntity::getUsername, username.trim());
        }
        if (success != null) {
            wrapper.eq(LoginLogEntity::getSuccess, success);
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new LoginLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getUserId(),
                        item.getUsername(),
                        item.getLoginType(),
                        item.getLoginIp(),
                        item.getSuccess(),
                        item.getFailReason(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    public List<OperationLogResponse> listOperationLogs(String bizModule, String operationType) {
        OperationLogMapper mapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .orderByDesc(OperationLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (bizModule != null && !bizModule.isBlank()) {
            wrapper.eq(OperationLogEntity::getBizModule, bizModule.trim());
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLogEntity::getOperationType, operationType.trim());
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new OperationLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getOperatorUserId(),
                        item.getOperatorName(),
                        item.getBizModule(),
                        item.getBizType(),
                        item.getBizId(),
                        item.getOperationType(),
                        item.getSuccess(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    public OperationLogDiffResponse getOperationLogDiff(Long id) {
        OperationLogMapper mapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        OperationLogEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("AUDIT_LOG_NOT_FOUND", "操作审计日志不存在: " + id);
        }
        String beforeRaw = (entity.getBeforeJson() != null && !entity.getBeforeJson().isBlank())
                ? entity.getBeforeJson()
                : entity.getRequestBody();
        String afterRaw = (entity.getAfterJson() != null && !entity.getAfterJson().isBlank())
                ? entity.getAfterJson()
                : entity.getResponseBody();

        List<FieldDiffItem> diffs = computeJsonDiff(beforeRaw, afterRaw);
        return new OperationLogDiffResponse(
                entity.getId(),
                entity.getBizModule(),
                entity.getBizType(),
                entity.getBizId(),
                entity.getOperationType(),
                entity.getOperatorName(),
                entity.getOccurredAt(),
                beforeRaw,
                afterRaw,
                diffs
        );
    }

    public List<BlacklistItemResponse> listBlacklist() {
        SecBlacklistMapper mapper = required(blacklistMapperProvider, "黑名单管理需要数据库连接");
        List<SecBlacklistEntity> rows = mapper.selectList(
                new LambdaQueryWrapper<SecBlacklistEntity>()
                        .eq(SecBlacklistEntity::getDeleted, 0)
                        .orderByDesc(SecBlacklistEntity::getId)
        );
        Map<Long, String> userNames = resolveOperatorNames(rows.stream()
                .map(SecBlacklistEntity::getCreatedBy)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList());
        LocalDateTime now = LocalDateTime.now();
        return rows.stream()
                .map(item -> toBlacklistResponse(item, userNames, now))
                .toList();
    }

    public BlacklistItemResponse addBlacklist(BlacklistCreateRequest request) {
        SecBlacklistMapper mapper = required(blacklistMapperProvider, "黑名单管理需要数据库连接");
        String type = request.type() != null ? request.type().trim().toUpperCase(Locale.ROOT) : "";
        if (!VALID_BLACKLIST_TYPES.contains(type)) {
            throw new BizException("INVALID_BLACKLIST_TYPE", "不支持的封禁类型: " + type);
        }
        String value = request.targetValue() != null ? request.targetValue().trim() : "";
        if (value.isEmpty()) {
            throw new BizException("INVALID_BLACKLIST_TARGET", "封禁目标值不能为空");
        }
        Long tenantId = currentTenantId();
        SecBlacklistEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<SecBlacklistEntity>()
                        .eq(SecBlacklistEntity::getTenantId, tenantId)
                        .eq(SecBlacklistEntity::getBlackType, type)
                        .eq(SecBlacklistEntity::getBlackValue, value)
                        .eq(SecBlacklistEntity::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            throw new BizException("BLACKLIST_ALREADY_EXISTS", "该拦截目标已存在于黑名单中: " + value);
        }

        SecBlacklistEntity entity = new SecBlacklistEntity();
        entity.setTenantId(tenantId);
        entity.setBlackType(type);
        entity.setBlackValue(value);
        entity.setReason(request.reason() != null && !request.reason().isBlank() ? request.reason().trim() : "管理员手动封禁处置");
        if (request.expireDays() != null && request.expireDays() > 0) {
            entity.setExpiredAt(LocalDateTime.now().plusDays(request.expireDays()));
        }
        entity.setStatus("ENABLED");
        mapper.insert(entity);
        invalidateBlacklistCache();
        recordAudit("sec_blacklist", String.valueOf(entity.getId()), "BLOCK", type + ":" + value, entity.getReason());

        SecBlacklistEntity inserted = mapper.selectById(entity.getId());
        Map<Long, String> names = resolveOperatorNames(
                inserted != null && inserted.getCreatedBy() != null ? List.of(inserted.getCreatedBy()) : List.of()
        );
        return toBlacklistResponse(inserted != null ? inserted : entity, names, LocalDateTime.now());
    }

    public void removeBlacklist(Long id) {
        SecBlacklistMapper mapper = required(blacklistMapperProvider, "黑名单管理需要数据库连接");
        SecBlacklistEntity existing = mapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getDeleted())) {
            throw new BizException("BLACKLIST_NOT_FOUND", "黑名单记录不存在: " + id);
        }
        mapper.deleteById(id);
        invalidateBlacklistCache();
        recordAudit("sec_blacklist", String.valueOf(id), "UNBLOCK", existing.getBlackValue(), "RELEASED");
    }

    @Override
    public void assertAllowed(Long tenantId, String username, String clientIp) {
        CachedBlacklistSnapshot snapshot = loadBlacklistSnapshot();
        if (snapshot == null || !snapshot.enabled() || snapshot.rows().isEmpty()) {
            return;
        }
        String normalizedUser = username != null ? username.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedIp = clientIp != null ? clientIp.trim() : "";
        String tenantIdText = tenantId != null ? String.valueOf(tenantId) : "";

        for (CachedBlacklistRow row : snapshot.rows()) {
            boolean scopeMatches = Objects.equals(1L, row.tenantId())
                    || Objects.equals(0L, row.tenantId())
                    || (tenantId != null && Objects.equals(tenantId, row.tenantId()));
            if (!scopeMatches) {
                continue;
            }
            String blackVal = row.blackValue() != null ? row.blackValue().trim() : "";
            if (blackVal.isEmpty()) {
                continue;
            }
            boolean hit = switch (row.blackType()) {
                case "IP" -> !normalizedIp.isEmpty() && blackVal.equalsIgnoreCase(normalizedIp);
                case "USER" -> !normalizedUser.isEmpty() && blackVal.equalsIgnoreCase(normalizedUser);
                case "TENANT" -> !tenantIdText.isEmpty() && blackVal.equalsIgnoreCase(tenantIdText);
                default -> false;
            };
            if (hit) {
                String reason = (row.reason() != null && !row.reason().isBlank()) ? row.reason() : "命中高危安全黑名单阻断规则";
                throw new BizException("SECURITY_BLACKLIST_BLOCKED", "当前访问已被安全黑名单拦截: " + reason);
            }
        }
    }

    public List<RiskRuleResponse> listRiskRules() {
        RiskRuleMapper mapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");
        Long tenantId = currentTenantId();
        List<RiskRuleEntity> rules = mapper.selectList(
                new LambdaQueryWrapper<RiskRuleEntity>()
                        .eq(RiskRuleEntity::getTenantId, tenantId)
                        .eq(RiskRuleEntity::getDeleted, 0)
                        .orderByAsc(RiskRuleEntity::getId)
        );
        if (rules.isEmpty() && !Objects.equals(1L, tenantId)) {
            try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                    TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Fallback read default risk rules")) {
                rules = mapper.selectList(
                        new LambdaQueryWrapper<RiskRuleEntity>()
                                .eq(RiskRuleEntity::getTenantId, 1L)
                                .eq(RiskRuleEntity::getDeleted, 0)
                                .orderByAsc(RiskRuleEntity::getId)
                );
            }
        }
        return rules.stream().map(this::toRiskRuleResponse).toList();
    }

    public RiskRuleResponse updateRiskRuleStatus(Long id, boolean enabled) {
        RiskRuleMapper mapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");
        RiskRuleEntity entity = mapper.selectById(id);
        if (entity == null || Integer.valueOf(1).equals(entity.getDeleted())) {
            throw new BizException("RISK_RULE_NOT_FOUND", "风控规则不存在: " + id);
        }
        String prevStatus = entity.getStatus();
        String nextStatus = enabled ? "ENABLED" : "DISABLED";
        entity.setStatus(nextStatus);
        mapper.updateById(entity);
        recordAudit("sec_risk_rule", String.valueOf(id), "STATUS", prevStatus, nextStatus);
        return toRiskRuleResponse(entity);
    }

    public RiskEvaluateResponse evaluateRiskRule(RiskEvaluateRequest request) {
        long startNs = System.nanoTime();
        RiskRuleMapper mapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");
        Long tenantId = currentTenantId();
        RiskRuleEntity rule = mapper.selectOne(
                new LambdaQueryWrapper<RiskRuleEntity>()
                        .eq(RiskRuleEntity::getTenantId, tenantId)
                        .eq(RiskRuleEntity::getRuleCode, request.ruleCode().trim())
                        .eq(RiskRuleEntity::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (rule == null && !Objects.equals(1L, tenantId)) {
            try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                    TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Fallback read baseline risk rule")) {
                rule = mapper.selectOne(
                        new LambdaQueryWrapper<RiskRuleEntity>()
                                .eq(RiskRuleEntity::getTenantId, 1L)
                                .eq(RiskRuleEntity::getRuleCode, request.ruleCode().trim())
                                .eq(RiskRuleEntity::getDeleted, 0)
                                .last("LIMIT 1")
                );
            }
        }
        if (rule == null) {
            throw new BizException("RISK_RULE_NOT_FOUND", "风控规则不存在: " + request.ruleCode());
        }

        int threshold = extractThreshold(rule.getActionJson(), rule.getRuleCode());
        int windowMinutes = extractWindowMinutes(rule.getActionJson());
        String actionCode = extractAction(rule.getActionJson());
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

        long recentFailures = countRecentLoginFailures(tenantId, request.sampleAccount(), request.sampleIp(), since);
        long recentOps = countRecentOperations(tenantId, request.sampleAccount(), since);
        int submittedFrequency = request.frequency() != null ? request.frequency() : 0;

        long observedMetric = "RULE_EXPORT_BURST".equals(rule.getRuleCode()) ? recentOps : recentFailures;
        long effectiveValue = Math.max(submittedFrequency, observedMetric);
        boolean triggered = "ENABLED".equalsIgnoreCase(rule.getStatus()) && effectiveValue >= threshold;

        String decision = !"ENABLED".equalsIgnoreCase(rule.getStatus())
                ? "RULE_DISABLED_BYPASS"
                : (triggered ? "ALERT_AND_BLOCK" : "ALLOW_WITH_LOG");
        String enforcedAction = triggered
                ? formatEnforcedAction(actionCode, request.sampleIp(), windowMinutes)
                : ("ENABLED".equalsIgnoreCase(rule.getStatus()) ? "仅记入审计日志（未达到阻断阈值）" : "规则已禁用，放行");
        String matchedThreshold = String.format(
                "%d 分钟窗口阈值 >= %d 次（实测日志命中 %d 次，演练输入 %d 次）",
                windowMinutes, threshold, observedMetric, submittedFrequency
        );

        double elapsedMs = Math.max(0.01, (System.nanoTime() - startNs) / 1_000_000.0);
        return new RiskEvaluateResponse(
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                rule.getRuleCode(),
                decision,
                matchedThreshold,
                enforcedAction,
                String.format(Locale.US, "%.2fms", elapsedMs),
                recentFailures,
                recentOps
        );
    }

    public List<HighRiskActionItemResponse> listHighRiskCatalog() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        if (jdbcTemplate == null) {
            return List.of();
        }
        Long tenantId = currentTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT resource_code, resource_name, http_method, url_pattern
                FROM iam_api_resource
                WHERE tenant_id = ?
                  AND deleted = 0
                  AND status = 'ENABLED'
                  AND UPPER(http_method) <> 'GET'
                ORDER BY id ASC
                """,
                tenantId
        );
        if (rows.isEmpty() && !Objects.equals(1L, tenantId)) {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT resource_code, resource_name, http_method, url_pattern
                    FROM iam_api_resource
                    WHERE tenant_id = 1
                      AND deleted = 0
                      AND status = 'ENABLED'
                      AND UPPER(http_method) <> 'GET'
                    ORDER BY id ASC
                    """
            );
        }
        return rows.stream().map(this::toHighRiskCatalogItem).toList();
    }

    private HighRiskActionItemResponse toHighRiskCatalogItem(Map<String, Object> row) {
        String code = String.valueOf(row.getOrDefault("resource_code", ""));
        String name = String.valueOf(row.getOrDefault("resource_name", code));
        String method = String.valueOf(row.getOrDefault("http_method", "POST")).toUpperCase(Locale.ROOT);
        String urlPattern = String.valueOf(row.getOrDefault("url_pattern", ""));
        String lower = (code + " " + urlPattern).toLowerCase(Locale.ROOT);

        String category = resolveCategory(lower);
        String riskLevel = resolveRiskLevel(method, lower);
        boolean requireApproval = "P0_CRITICAL".equals(riskLevel);
        String maskingPolicy = switch (riskLevel) {
            case "P0_CRITICAL" -> "敏感凭据全遮蔽 · 强制保留变更前后 Diff";
            case "P1_HIGH" -> "手机号/密钥字段掩码脱敏 · 全链路留痕";
            default -> "标准操作审计留存";
        };
        int retentionDays = switch (riskLevel) {
            case "P0_CRITICAL" -> 365;
            case "P1_HIGH" -> 180;
            default -> 90;
        };
        return new HighRiskActionItemResponse(
                code,
                name,
                method,
                urlPattern,
                category,
                riskLevel,
                requireApproval,
                maskingPolicy,
                retentionDays
        );
    }

    private String resolveCategory(String lower) {
        if (lower.contains("tenant")) {
            return "TENANT";
        }
        if (lower.contains("integration") || lower.contains("datasource") || lower.contains("webhook")) {
            return "INTEGRATION";
        }
        if (lower.contains("scheduler")) {
            return "SCHEDULER";
        }
        if (lower.contains("audit") || lower.contains("export") || lower.contains("file")) {
            return "DATA";
        }
        if (lower.contains("system") || lower.contains("config")) {
            return "SYSTEM";
        }
        return "IAM";
    }

    private String resolveRiskLevel(String method, String lower) {
        if ("DELETE".equals(method)
                || lower.contains("role-api")
                || lower.contains("isolation")
                || lower.contains("security")
                || lower.contains("crypto")
                || lower.contains("blacklist")) {
            return "P0_CRITICAL";
        }
        if ("PUT".equals(method)
                || "PATCH".equals(method)
                || lower.contains("grant")
                || lower.contains("export")
                || lower.contains("reset")
                || lower.contains("password")) {
            return "P1_HIGH";
        }
        return "P2_MEDIUM";
    }

    private BlacklistItemResponse toBlacklistResponse(SecBlacklistEntity entity, Map<Long, String> names, LocalDateTime now) {
        String effectiveStatus = entity.getExpiredAt() != null && entity.getExpiredAt().isBefore(now)
                ? "EXPIRED"
                : ("DISABLED".equalsIgnoreCase(entity.getStatus()) ? "EXPIRED" : "ACTIVE");
        String operator = names.getOrDefault(
                entity.getCreatedBy(),
                entity.getCreatedBy() != null && entity.getCreatedBy() > 0 ? "user#" + entity.getCreatedBy() : "system"
        );
        return new BlacklistItemResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getBlackType(),
                entity.getBlackValue(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getExpiredAt(),
                effectiveStatus,
                operator
        );
    }

    private RiskRuleResponse toRiskRuleResponse(RiskRuleEntity entity) {
        return new RiskRuleResponse(
                entity.getId(),
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getRiskType(),
                entity.getRuleExpr(),
                extractAction(entity.getActionJson()),
                entity.getRemark() != null ? entity.getRemark() : entity.getRuleExpr(),
                "ENABLED".equalsIgnoreCase(entity.getStatus())
        );
    }

    private long countRecentLoginFailures(Long tenantId, String sampleAccount, String sampleIp, LocalDateTime since) {
        LoginLogMapper mapper = loginLogMapperProvider != null ? loginLogMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            return 0L;
        }
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .eq(LoginLogEntity::getTenantId, tenantId)
                .eq(LoginLogEntity::getSuccess, false)
                .ge(LoginLogEntity::getOccurredAt, since);
        if (sampleAccount != null && !sampleAccount.isBlank()) {
            wrapper.eq(LoginLogEntity::getUsername, sampleAccount.trim());
        } else if (sampleIp != null && !sampleIp.isBlank()) {
            wrapper.eq(LoginLogEntity::getLoginIp, sampleIp.trim());
        }
        Long count = mapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    private long countRecentOperations(Long tenantId, String sampleAccount, LocalDateTime since) {
        OperationLogMapper mapper = operationLogMapperProvider != null ? operationLogMapperProvider.getIfAvailable() : null;
        if (mapper == null) {
            return 0L;
        }
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getTenantId, tenantId)
                .ge(OperationLogEntity::getOccurredAt, since);
        if (sampleAccount != null && !sampleAccount.isBlank()) {
            wrapper.eq(OperationLogEntity::getOperatorName, sampleAccount.trim());
        }
        Long count = mapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    private int extractThreshold(String actionJson, String ruleCode) {
        if (actionJson != null && !actionJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(actionJson);
                if (node.has("threshold") && node.get("threshold").isInt()) {
                    return Math.max(1, node.get("threshold").asInt());
                }
            } catch (Exception ignored) {
            }
        }
        if ("RULE_EXPORT_BURST".equals(ruleCode) || "RULE_GEO_ANOMALY".equals(ruleCode)) {
            return 3;
        }
        if ("RULE_PRIV_ESCALATION".equals(ruleCode)) {
            return 1;
        }
        return 5;
    }

    private int extractWindowMinutes(String actionJson) {
        if (actionJson != null && !actionJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(actionJson);
                if (node.has("windowMinutes") && node.get("windowMinutes").isInt()) {
                    return Math.max(1, node.get("windowMinutes").asInt());
                }
            } catch (Exception ignored) {
            }
        }
        return 5;
    }

    private String extractAction(String actionJson) {
        if (actionJson != null && !actionJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(actionJson);
                if (node.hasNonNull("action")) {
                    return node.get("action").asText();
                }
            } catch (Exception ignored) {
            }
        }
        return "ALERT_ONLY";
    }

    private String formatEnforcedAction(String actionCode, String sampleIp, int windowMinutes) {
        String ipText = (sampleIp != null && !sampleIp.isBlank()) ? sampleIp : "当前来源 IP";
        return switch (actionCode) {
            case "LOCK_ACCOUNT" -> String.format("临时锁定目标账号及 IP (%s) %d 分钟，并发送安全告警", ipText, windowMinutes * 3);
            case "REQUIRE_APPROVAL" -> "阻断超限批量导出 · 触发主管双人审批工单";
            case "REVOKE_SESSION" -> "立即吊销当前会话令牌并记入安全阻断审计";
            case "TRIGGER_MFA" -> "强制要求 MFA 二次认证验证后放行";
            default -> "触发安全风控告警";
        };
    }

    private CachedBlacklistSnapshot loadBlacklistSnapshot() {
        long nowMs = System.currentTimeMillis();
        CachedBlacklistSnapshot cached = blacklistSnapshot;
        if (cached != null && nowMs < cached.expiresAtMs()) {
            return cached;
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        if (jdbcTemplate == null) {
            return null;
        }
        boolean enabled = isBlacklistEngineEnabled(jdbcTemplate);
        List<CachedBlacklistRow> rows = new ArrayList<>();
        if (enabled) {
            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    """
                    SELECT tenant_id, black_type, black_value, reason, expired_at
                    FROM sec_blacklist
                    WHERE deleted = 0
                      AND status = 'ENABLED'
                    """
            );
            for (Map<String, Object> row : records) {
                Object expObj = row.get("expired_at");
                if (expObj instanceof Timestamp ts && ts.toLocalDateTime().isBefore(now)) {
                    continue;
                }
                if (expObj instanceof LocalDateTime ldt && ldt.isBefore(now)) {
                    continue;
                }
                Long tId = row.get("tenant_id") instanceof Number num ? num.longValue() : 1L;
                String bType = row.get("black_type") != null ? String.valueOf(row.get("black_type")).toUpperCase(Locale.ROOT) : "";
                String bVal = row.get("black_value") != null ? String.valueOf(row.get("black_value")) : "";
                String reason = row.get("reason") != null ? String.valueOf(row.get("reason")) : "";
                rows.add(new CachedBlacklistRow(tId, bType, bVal, reason));
            }
        }
        CachedBlacklistSnapshot refreshed = new CachedBlacklistSnapshot(nowMs + BLACKLIST_CACHE_TTL_MS, enabled, rows);
        blacklistSnapshot = refreshed;
        return refreshed;
    }

    private boolean isBlacklistEngineEnabled(JdbcTemplate jdbcTemplate) {
        try {
            List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                    """
                    SELECT config_value
                    FROM sys_config
                    WHERE config_key = 'audit.policy'
                      AND deleted = 0
                      AND status = 'ENABLED'
                    ORDER BY tenant_id ASC
                    LIMIT 1
                    """
            );
            if (!configs.isEmpty() && configs.get(0).get("config_value") != null) {
                JsonNode node = objectMapper.readTree(String.valueOf(configs.get(0).get("config_value")));
                if (node.has("blacklistEnabled")) {
                    return node.get("blacklistEnabled").asBoolean(true);
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private void invalidateBlacklistCache() {
        blacklistSnapshot = null;
    }

    private Map<Long, String> resolveOperatorNames(List<Long> userIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        if (jdbcTemplate == null || userIds == null || userIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username FROM iam_user WHERE id IN (" + placeholders + ")",
                userIds.toArray()
        );
        for (Map<String, Object> row : rows) {
            if (row.get("id") instanceof Number num && row.get("username") != null) {
                result.put(num.longValue(), String.valueOf(row.get("username")));
            }
        }
        return result;
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
        AuditTrailService trailService = auditTrailServiceProvider != null ? auditTrailServiceProvider.getIfAvailable() : null;
        if (trailService != null) {
            trailService.record("audit", bizType, bizId, opType, reqBody, respBody, true);
        }
    }

    public static List<FieldDiffItem> computeJsonDiff(String beforeJson, String afterJson) {
        ObjectMapper om = new ObjectMapper();
        Map<String, String> beforeMap = flattenToMap(om, beforeJson);
        Map<String, String> afterMap = flattenToMap(om, afterJson);
        TreeSet<String> allKeys = new TreeSet<>();
        allKeys.addAll(beforeMap.keySet());
        allKeys.addAll(afterMap.keySet());

        List<FieldDiffItem> items = new ArrayList<>();
        for (String key : allKeys) {
            boolean inBefore = beforeMap.containsKey(key);
            boolean inAfter = afterMap.containsKey(key);
            String beforeVal = beforeMap.get(key);
            String afterVal = afterMap.get(key);
            if (!inBefore && inAfter) {
                items.add(new FieldDiffItem(key, null, afterVal, "ADDED"));
            } else if (inBefore && !inAfter) {
                items.add(new FieldDiffItem(key, beforeVal, null, "REMOVED"));
            } else if (!Objects.equals(beforeVal, afterVal)) {
                items.add(new FieldDiffItem(key, beforeVal, afterVal, "MODIFIED"));
            }
        }
        return items;
    }

    private static Map<String, String> flattenToMap(ObjectMapper om, String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode root = om.readTree(json);
            flattenNode("", root, result);
        } catch (Exception ex) {
            result.put("$raw", json);
        }
        return result;
    }

    private static void flattenNode(String prefix, JsonNode node, Map<String, String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            if (!prefix.isEmpty()) {
                out.put(prefix, "null");
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenNode(key, entry.getValue(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenNode(prefix + "[" + i + "]", node.get(i), out);
            }
        } else {
            out.put(prefix.isEmpty() ? "value" : prefix, node.asText());
        }
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T bean = provider != null ? provider.getIfAvailable() : null;
        if (bean == null) {
            throw new IllegalStateException(message);
        }
        return bean;
    }
}
