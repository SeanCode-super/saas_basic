package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertEvaluationSummary;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertEventResponse;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertRuleResponse;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertRuleSaveRequest;
import com.saasbasics.platform.modules.system.entity.AlertEventEntity;
import com.saasbasics.platform.modules.system.entity.AlertRuleEntity;
import com.saasbasics.platform.modules.system.mapper.AlertEventMapper;
import com.saasbasics.platform.modules.system.mapper.AlertRuleMapper;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TelemetrySnapshot;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 可观测性阈值告警规则引擎与联动触达服务：
 * 定时采集 API P99 时延、错误率、传输防重放/明文拦截量、限流拦截量及 JVM 堆占用，触发冷却校验、站内信通知及企业 Webhook 推送。
 */
@Service
public class AlertGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(AlertGovernanceService.class);
    private static final String LOCK_KEY = "saas:monitor:alert:eval:lock";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final AlertRuleMapper ruleMapper;
    private final AlertEventMapper eventMapper;
    private final ObjectProvider<PlatformApiCatalogService> apiCatalogProvider;
    private final ObjectProvider<PlatformTelemetryService> telemetryProvider;
    private final ObjectProvider<TransportCryptoPolicyService> transportPolicyProvider;
    private final ObjectProvider<SystemNotifyService> notifyServiceProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AlertGovernanceService(
            AlertRuleMapper ruleMapper,
            AlertEventMapper eventMapper,
            ObjectProvider<PlatformApiCatalogService> apiCatalogProvider,
            ObjectProvider<PlatformTelemetryService> telemetryProvider,
            ObjectProvider<TransportCryptoPolicyService> transportPolicyProvider,
            ObjectProvider<SystemNotifyService> notifyServiceProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.apiCatalogProvider = apiCatalogProvider;
        this.telemetryProvider = telemetryProvider;
        this.transportPolicyProvider = transportPolicyProvider;
        this.notifyServiceProvider = notifyServiceProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public List<AlertRuleResponse> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlertRuleEntity>()
                        .orderByAsc(AlertRuleEntity::getId))
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertRuleResponse createRule(AlertRuleSaveRequest request) {
        AlertRuleEntity dup = ruleMapper.selectOne(new LambdaQueryWrapper<AlertRuleEntity>()
                .eq(AlertRuleEntity::getRuleCode, request.ruleCode().trim()));
        if (dup != null) {
            throw new BizException("ALERT_RULE_EXISTS", "告警规则编码已存在: " + request.ruleCode());
        }
        AlertRuleEntity entity = new AlertRuleEntity();
        applyRuleRequest(entity, request);
        ruleMapper.insert(entity);
        return toRuleResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertRuleResponse updateRule(Long id, AlertRuleSaveRequest request) {
        AlertRuleEntity entity = ruleMapper.selectById(id);
        if (entity == null) {
            throw new BizException("ALERT_RULE_NOT_FOUND", "告警规则不存在");
        }
        applyRuleRequest(entity, request);
        ruleMapper.updateById(entity);
        return toRuleResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
    }

    public List<AlertEventResponse> listEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return eventMapper.selectList(new LambdaQueryWrapper<AlertEventEntity>()
                        .orderByDesc(AlertEventEntity::getId)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse acknowledgeEvent(Long id) {
        AlertEventEntity entity = eventMapper.selectById(id);
        if (entity == null) {
            throw new BizException("ALERT_EVENT_NOT_FOUND", "告警事件不存在");
        }
        AuthPrincipal principal = AuthContext.get();
        String operator = principal != null && principal.username() != null ? principal.username() : "admin";
        entity.setAcknowledged(true);
        entity.setAcknowledgedBy(operator);
        entity.setAcknowledgedAt(LocalDateTime.now());
        eventMapper.updateById(entity);
        return toEventResponse(entity);
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scheduledEvaluate() {
        String lockToken = UUID.randomUUID().toString();
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        boolean lockedByRedis = false;
        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(LOCK_KEY, lockToken, Duration.ofSeconds(50));
                if (!Boolean.TRUE.equals(ok)) {
                    return;
                }
                lockedByRedis = true;
            } catch (Exception ignored) {
                // Fallback when Redis unavailable
            }
        }
        try {
            evaluateAllRules(false);
        } catch (Exception ex) {
            log.warn("[AlertEngine] 定时巡检异常: {}", ex.getMessage());
        } finally {
            if (lockedByRedis && redis != null) {
                try {
                    redis.execute(UNLOCK_SCRIPT, List.of(LOCK_KEY), lockToken);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AlertEvaluationSummary evaluateAllRules(boolean ignoreCooldown) {
        List<AlertRuleEntity> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlertRuleEntity>()
                .eq(AlertRuleEntity::getEnabled, true)
                .orderByAsc(AlertRuleEntity::getId));
        if (rules.isEmpty()) {
            return new AlertEvaluationSummary(0, 0, List.of());
        }

        MetricSample sample = collectCurrentMetrics();
        LocalDateTime now = LocalDateTime.now();
        List<AlertEventResponse> triggered = new ArrayList<>();

        for (AlertRuleEntity rule : rules) {
            if (!ignoreCooldown && rule.getLastTriggeredAt() != null) {
                int cooldown = rule.getCooldownSeconds() != null ? rule.getCooldownSeconds() : 300;
                if (rule.getLastTriggeredAt().plusSeconds(cooldown).isAfter(now)) {
                    continue;
                }
            }
            MetricEvaluation eval = evaluateRule(rule, sample);
            if (!eval.matched()) {
                continue;
            }
            AlertEventEntity event = persistAndDispatchEvent(rule, eval, now);
            rule.setLastTriggeredAt(now);
            ruleMapper.updateById(rule);
            triggered.add(toEventResponse(event));
        }

        return new AlertEvaluationSummary(rules.size(), triggered.size(), triggered);
    }

    private MetricSample collectCurrentMetrics() {
        double maxP99 = 0.0;
        String maxP99Endpoint = "-";
        long totalCalls = 0L;
        long errorCalls = 0L;

        PlatformApiCatalogService catalog = apiCatalogProvider != null ? apiCatalogProvider.getIfAvailable() : null;
        if (catalog != null) {
            PlatformApiCatalogService.ApiCatalogSnapshot snap = catalog.buildSnapshot(null);
            totalCalls = snap.summary().totalCalls();
            errorCalls = snap.summary().errorCalls();
            for (PlatformApiCatalogService.ApiEndpointStatView ep : snap.endpoints()) {
                if (ep.p99LatencyMs() > maxP99) {
                    maxP99 = ep.p99LatencyMs();
                    maxP99Endpoint = ep.httpMethod() + " " + ep.pathPattern();
                }
            }
        }

        double errorRatePct = totalCalls > 0 ? Math.round((errorCalls * 10000.0) / totalCalls) / 100.0 : 0.0;

        long transportRejected = 0L;
        TransportCryptoPolicyService policyService = transportPolicyProvider != null
                ? transportPolicyProvider.getIfAvailable()
                : null;
        if (policyService != null) {
            TransportCryptoPolicyService.TelemetryStats ts = policyService.getTelemetryStats();
            transportRejected = ts.rejectedPlaintext() + ts.rejectedReplay() + ts.decryptErrors();
        }

        long rateLimitRejected = 0L;
        PlatformTelemetryService telemetry = telemetryProvider != null ? telemetryProvider.getIfAvailable() : null;
        if (telemetry != null) {
            TelemetrySnapshot ts = telemetry.getSnapshot();
            rateLimitRejected = ts.rateLimitRejected();
        }

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double heapPct = heap.getMax() > 0 ? Math.round((heap.getUsed() * 10000.0) / heap.getMax()) / 100.0 : 0.0;

        return new MetricSample(maxP99, maxP99Endpoint, errorRatePct, transportRejected, rateLimitRejected, heapPct);
    }

    private MetricEvaluation evaluateRule(AlertRuleEntity rule, MetricSample sample) {
        String metric = rule.getMetricType() != null ? rule.getMetricType().toUpperCase(Locale.ROOT) : "API_P99_MS";
        double threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
        String op = rule.getComparisonOp() != null ? rule.getComparisonOp().toUpperCase(Locale.ROOT) : "GTE";

        double value;
        String target;
        switch (metric) {
            case "API_ERROR_RATE" -> {
                value = sample.errorRatePct();
                target = "ALL_HTTP_ENDPOINTS";
            }
            case "TRANSPORT_REJECTED" -> {
                value = sample.transportRejected();
                target = "TRANSPORT_CRYPTO_FILTER";
            }
            case "RATE_LIMIT_REJECTED" -> {
                value = sample.rateLimitRejected();
                target = "SLIDING_WINDOW_LIMITER";
            }
            case "JVM_HEAP_PERCENT" -> {
                value = sample.jvmHeapPct();
                target = "JVM_HEAP";
            }
            default -> {
                value = sample.maxP99Ms();
                target = sample.maxP99Endpoint();
            }
        }

        boolean matched = "GT".equals(op) ? value > threshold : value >= threshold;
        return new MetricEvaluation(matched, value, threshold, target);
    }

    private AlertEventEntity persistAndDispatchEvent(AlertRuleEntity rule, MetricEvaluation eval, LocalDateTime now) {
        String msg = String.format(
                "[%s] %s 触发阈值告警：当前值 %.2f (阈值 %.2f)，目标对象: %s",
                rule.getSeverity(),
                rule.getRuleName(),
                eval.value(),
                eval.threshold(),
                eval.target()
        );

        String channels = rule.getNotifyChannels() != null ? rule.getNotifyChannels().toUpperCase(Locale.ROOT) : "IN_APP";
        String dispatchStatus = "LOCAL_ONLY";
        StringBuilder dispatchLog = new StringBuilder();

        if (channels.contains("IN_APP")) {
            SystemNotifyService notifyService = notifyServiceProvider != null ? notifyServiceProvider.getIfAvailable() : null;
            if (notifyService != null) {
                try {
                    notifyService.createAndSend(1L, 1L, "ALERT", "[" + rule.getSeverity() + "] " + rule.getRuleName(),
                            msg, "SYS_ALERT_RULE", rule.getRuleCode());
                    dispatchLog.append("IN_APP:OK ");
                } catch (Exception ex) {
                    dispatchLog.append("IN_APP:ERR(").append(ex.getMessage()).append(") ");
                }
            }
        }

        if (channels.contains("WEBHOOK") && rule.getWebhookUrl() != null && !rule.getWebhookUrl().isBlank()) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("msgtype", "text");
                body.put("text", Map.of("content", msg));
                body.put("ruleCode", rule.getRuleCode());
                body.put("severity", rule.getSeverity());
                body.put("metricType", rule.getMetricType());
                body.put("metricValue", eval.value());
                body.put("threshold", eval.threshold());
                body.put("target", eval.target());
                body.put("timestamp", now.toString());

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(rule.getWebhookUrl().trim()))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(4))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    dispatchStatus = "SENT";
                    dispatchLog.append("WEBHOOK:HTTP_").append(resp.statusCode());
                } else {
                    dispatchStatus = "FAILED";
                    dispatchLog.append("WEBHOOK:HTTP_").append(resp.statusCode());
                }
            } catch (Exception ex) {
                dispatchStatus = "FAILED";
                dispatchLog.append("WEBHOOK:ERR(").append(ex.getMessage()).append(")");
            }
        }

        AlertEventEntity event = new AlertEventEntity();
        event.setRuleId(rule.getId());
        event.setRuleCode(rule.getRuleCode());
        event.setRuleName(rule.getRuleName());
        event.setSeverity(rule.getSeverity());
        event.setMetricType(rule.getMetricType());
        event.setMetricValue(eval.value());
        event.setThresholdValue(eval.threshold());
        event.setTargetEndpoint(eval.target());
        event.setMessage(msg);
        event.setDispatchStatus(dispatchStatus);
        event.setDispatchDetail(dispatchLog.toString().trim());
        event.setAcknowledged(false);
        event.setTriggeredAt(now);
        event.setCreatedAt(now);
        eventMapper.insert(event);
        return event;
    }

    private void applyRuleRequest(AlertRuleEntity entity, AlertRuleSaveRequest req) {
        entity.setRuleCode(req.ruleCode().trim().toUpperCase(Locale.ROOT));
        entity.setRuleName(req.ruleName().trim());
        entity.setMetricType(req.metricType().trim().toUpperCase(Locale.ROOT));
        entity.setComparisonOp(req.comparisonOp() != null ? req.comparisonOp().trim().toUpperCase(Locale.ROOT) : "GTE");
        entity.setThresholdValue(req.thresholdValue());
        entity.setSeverity(req.severity() != null ? req.severity().trim().toUpperCase(Locale.ROOT) : "WARNING");
        entity.setCooldownSeconds(req.cooldownSeconds() != null ? req.cooldownSeconds() : 300);
        entity.setNotifyChannels(req.notifyChannels() != null && !req.notifyChannels().isBlank()
                ? req.notifyChannels().trim().toUpperCase(Locale.ROOT)
                : "IN_APP");
        entity.setWebhookUrl(req.webhookUrl() != null ? req.webhookUrl().trim() : null);
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private AlertRuleResponse toRuleResponse(AlertRuleEntity e) {
        return new AlertRuleResponse(
                e.getId(),
                e.getRuleCode(),
                e.getRuleName(),
                e.getMetricType(),
                e.getComparisonOp(),
                e.getThresholdValue() != null ? e.getThresholdValue() : 0.0,
                e.getSeverity(),
                e.getCooldownSeconds() != null ? e.getCooldownSeconds() : 300,
                e.getNotifyChannels(),
                e.getWebhookUrl(),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getLastTriggeredAt()
        );
    }

    private AlertEventResponse toEventResponse(AlertEventEntity e) {
        return new AlertEventResponse(
                e.getId(),
                e.getRuleId(),
                e.getRuleCode(),
                e.getRuleName(),
                e.getSeverity(),
                e.getMetricType(),
                e.getMetricValue() != null ? e.getMetricValue() : 0.0,
                e.getThresholdValue() != null ? e.getThresholdValue() : 0.0,
                e.getTargetEndpoint(),
                e.getMessage(),
                e.getDispatchStatus(),
                e.getDispatchDetail(),
                Boolean.TRUE.equals(e.getAcknowledged()),
                e.getAcknowledgedBy(),
                e.getAcknowledgedAt(),
                e.getTriggeredAt()
        );
    }

    private record MetricSample(
            double maxP99Ms,
            String maxP99Endpoint,
            double errorRatePct,
            long transportRejected,
            long rateLimitRejected,
            double jvmHeapPct
    ) {
    }

    private record MetricEvaluation(
            boolean matched,
            double value,
            double threshold,
            String target
    ) {
    }
}
