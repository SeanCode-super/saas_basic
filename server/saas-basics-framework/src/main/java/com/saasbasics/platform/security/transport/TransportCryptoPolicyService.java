package com.saasbasics.platform.security.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 动态传输加密策略注册表与运行期遥测控制中枢（支持 DB 持久化、Redis 集群同步、路径规则及注解级覆盖）。
 */
@Service
public class TransportCryptoPolicyService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TransportCryptoPolicyService.class);
    private static final String REDIS_POLICY_KEY = "saas:transport:policy:snapshot";

    private final TransportCryptoProperties defaultProperties;
    private final ObjectProvider<TransportCryptoPolicyRepository> repositoryProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<PlatformApiCatalogService> apiCatalogProvider;
    private final RequestMappingHandlerMapping handlerMapping;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final List<AnnotationRule> annotationRules = new CopyOnWriteArrayList<>();

    private volatile TransportCryptoPolicySnapshot currentPolicy;
    private volatile long lastRedisSyncMillis = 0L;

    // 实时运行态遥测计数
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder encryptedRequests = new LongAdder();
    private final LongAdder plaintextAllowed = new LongAdder();
    private final LongAdder rejectedPlaintext = new LongAdder();
    private final LongAdder rejectedReplay = new LongAdder();
    private final LongAdder decryptErrors = new LongAdder();

    private record AnnotationRule(List<String> patterns, TransportCrypto.Rule rule) {
    }

    public record TelemetryStats(
            long totalRequests,
            long encryptedRequests,
            long plaintextAllowed,
            long rejectedPlaintext,
            long rejectedReplay,
            long decryptErrors
    ) {
    }

    public enum EndpointAction {
        BYPASS,
        OPTIONAL,
        REQUIRED
    }

    public TransportCryptoPolicyService(
            TransportCryptoProperties defaultProperties,
            ObjectProvider<TransportCryptoPolicyRepository> repositoryProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider) {
        this(defaultProperties, repositoryProvider, redisTemplateProvider, objectMapperProvider,
                meterRegistryProvider, handlerMappingProvider, null);
    }

    @Autowired
    public TransportCryptoPolicyService(
            TransportCryptoProperties defaultProperties,
            ObjectProvider<TransportCryptoPolicyRepository> repositoryProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider,
            ObjectProvider<PlatformApiCatalogService> apiCatalogProvider) {
        this.defaultProperties = defaultProperties;
        this.repositoryProvider = repositoryProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.meterRegistryProvider = meterRegistryProvider;
        this.handlerMapping = handlerMappingProvider.getIfAvailable();
        this.apiCatalogProvider = apiCatalogProvider;
        this.currentPolicy = buildFromProperties(1L);
    }

    @Override
    public void afterSingletonsInstantiated() {
        indexControllerAnnotations();
        reloadPolicy();
    }

    public TransportCryptoPolicySnapshot getCurrentPolicy() {
        syncFromRedisIfStale();
        return currentPolicy;
    }

    public synchronized TransportCryptoPolicySnapshot reloadPolicy() {
        TransportCryptoPolicyRepository repo = repositoryProvider.getIfAvailable();
        if (repo != null) {
            try {
                TransportCryptoPolicySnapshot stored = repo.loadGlobalPolicy();
                if (stored != null) {
                    this.currentPolicy = stored;
                    cacheToRedis(stored);
                    return stored;
                }
            } catch (Exception ex) {
                log.warn("[TransportCrypto] 从数据库加载传输策略失败，回退至默认配置: {}", ex.getMessage());
            }
        }
        return this.currentPolicy;
    }

    public synchronized TransportCryptoPolicySnapshot updatePolicy(TransportCryptoPolicySnapshot updated) {
        long nextVersion = Math.max(this.currentPolicy.policyVersion() + 1L, System.currentTimeMillis());
        int rotationHours = updated.keyRotationHours() > 0
                ? Math.max(1, Math.min(720, updated.keyRotationHours()))
                : Math.max(1, this.currentPolicy.keyRotationHours());
        int graceKeys = updated.maxGraceKeys() > 0
                ? Math.max(1, Math.min(10, updated.maxGraceKeys()))
                : Math.max(1, this.currentPolicy.maxGraceKeys());

        TransportCryptoPolicySnapshot normalized = new TransportCryptoPolicySnapshot(
                updated.mode() != null ? updated.mode() : TransportCryptoMode.OPTIONAL,
                updated.scopeMode() != null ? updated.scopeMode() : TransportScopeMode.ALL_API,
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                updated.encryptRequest(),
                updated.encryptResponse(),
                updated.encryptQuery(),
                updated.replayProtection(),
                Math.max(30, Math.min(3600, updated.timestampSkewSeconds())),
                rotationHours,
                graceKeys,
                cleanPatterns(updated.includePaths(), List.of("/api/**")),
                mergeMandatoryExcludes(updated.excludePaths()),
                cleanPatterns(updated.sensitivePaths(), defaultProperties.getSensitivePaths()),
                nextVersion,
                Instant.now(),
                updated.remark()
        );

        TransportCryptoPolicyRepository repo = repositoryProvider.getIfAvailable();
        if (repo != null) {
            normalized = repo.saveGlobalPolicy(normalized);
        }
        this.currentPolicy = normalized;
        cacheToRedis(normalized);
        log.info("[TransportCrypto] 已更新传输加密策略: mode={}, scope={}, ver={}",
                normalized.mode(), normalized.scopeMode(), normalized.policyVersion());
        return normalized;
    }

    /**
     * 决定单个请求最终应用的传输策略（先内建豁免 -> 再自定义排除 -> 再注解覆盖 -> 再全局作用域与模式）。
     */
    public EndpointAction resolveEndpointAction(HttpServletRequest request) {
        TransportCryptoPolicySnapshot policy = getCurrentPolicy();
        if (!defaultProperties.isEnabled() || policy.mode() == TransportCryptoMode.OFF) {
            return EndpointAction.BYPASS;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return EndpointAction.BYPASS;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            return EndpointAction.BYPASS;
        }
        if (isAlwaysBypassPath(uri)) {
            return EndpointAction.BYPASS;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return EndpointAction.BYPASS;
        }
        if (matchesAny(uri, policy.excludePaths())) {
            return EndpointAction.BYPASS;
        }

        // Controller / Method 注解精确覆盖
        for (AnnotationRule rule : annotationRules) {
            if (matchesAny(uri, rule.patterns())) {
                return switch (rule.rule()) {
                    case PLAIN -> EndpointAction.BYPASS;
                    case OPTIONAL -> EndpointAction.OPTIONAL;
                    case REQUIRED -> policy.mode() == TransportCryptoMode.REQUIRED
                            ? EndpointAction.REQUIRED
                            : EndpointAction.OPTIONAL;
                };
            }
        }

        boolean inScope = switch (policy.scopeMode()) {
            case ALL_API -> matchesAny(uri, policy.includePaths());
            case SENSITIVE_ONLY -> matchesAny(uri, policy.sensitivePaths());
            case CUSTOM -> matchesAny(uri, policy.includePaths());
        };
        if (!inScope) {
            return EndpointAction.BYPASS;
        }
        return policy.mode() == TransportCryptoMode.REQUIRED ? EndpointAction.REQUIRED : EndpointAction.OPTIONAL;
    }

    public void recordOutcome(String outcome) {
        recordOutcome(null, null, outcome);
    }

    public void recordOutcome(String method, String uri, String outcome) {
        totalRequests.increment();
        switch (outcome) {
            case "encrypted" -> encryptedRequests.increment();
            case "plaintext" -> plaintextAllowed.increment();
            case "rejected_plaintext" -> rejectedPlaintext.increment();
            case "rejected_replay" -> rejectedReplay.increment();
            case "decrypt_error" -> decryptErrors.increment();
            default -> { }
        }
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            Counter.builder("saas_transport_crypto_requests_total")
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();
        }
        PlatformApiCatalogService catalog = apiCatalogProvider != null ? apiCatalogProvider.getIfAvailable() : null;
        if (catalog != null && uri != null) {
            catalog.recordTransportOutcome(method, uri, outcome);
        }
    }

    /**
     * 结合全局策略、白名单与注解规则，评估某个模板接口当前最终应用的加密策略与命中原因。
     */
    public PlatformApiCatalogService.PolicyDecision evaluateDecisionForPattern(String antPattern) {
        TransportCryptoPolicySnapshot policy = getCurrentPolicy();
        String probeUri = (antPattern == null ? "" : antPattern.replace("*", "sample"));
        if (!defaultProperties.isEnabled() || policy.mode() == TransportCryptoMode.OFF) {
            return new PlatformApiCatalogService.PolicyDecision("BYPASS", "全局传输加密已关闭 (OFF)", false);
        }
        if (isAlwaysBypassPath(probeUri)) {
            return new PlatformApiCatalogService.PolicyDecision("BYPASS", "系统强制豁免（策略协商/文件流/探针）", true);
        }
        if (matchesPatternRule(antPattern, probeUri, policy.excludePaths())) {
            return new PlatformApiCatalogService.PolicyDecision("BYPASS", "命中明文豁免白名单", false);
        }
        for (AnnotationRule rule : annotationRules) {
            if (matchesPatternRule(antPattern, probeUri, rule.patterns())) {
                return switch (rule.rule()) {
                    case PLAIN -> new PlatformApiCatalogService.PolicyDecision("BYPASS", "@TransportCrypto(PLAIN) 注解强制明文", false);
                    case OPTIONAL -> new PlatformApiCatalogService.PolicyDecision("OPTIONAL", "@TransportCrypto(OPTIONAL) 注解兼容加密", false);
                    case REQUIRED -> policy.mode() == TransportCryptoMode.REQUIRED
                            ? new PlatformApiCatalogService.PolicyDecision("REQUIRED", "@TransportCrypto(REQUIRED) 与全局强制模式一致", false)
                            : new PlatformApiCatalogService.PolicyDecision("OPTIONAL", "@TransportCrypto(REQUIRED)（兼容模式下支持密文协商）", false);
                };
            }
        }
        boolean inScope = switch (policy.scopeMode()) {
            case ALL_API -> matchesPatternRule(antPattern, probeUri, policy.includePaths());
            case SENSITIVE_ONLY -> matchesPatternRule(antPattern, probeUri, policy.sensitivePaths());
            case CUSTOM -> matchesPatternRule(antPattern, probeUri, policy.includePaths());
        };
        if (!inScope) {
            String reason = policy.scopeMode() == TransportScopeMode.SENSITIVE_ONLY
                    ? "未列入高敏接口清单（当前明文旁路）"
                    : "未命中包含路径范围（当前明文旁路）";
            return new PlatformApiCatalogService.PolicyDecision("BYPASS", reason, false);
        }
        String scopeReason = switch (policy.scopeMode()) {
            case ALL_API -> "全量业务接口范围覆盖";
            case SENSITIVE_ONLY -> "命中高敏接口清单";
            case CUSTOM -> "命中自定义包含路径规则";
        };
        return policy.mode() == TransportCryptoMode.REQUIRED
                ? new PlatformApiCatalogService.PolicyDecision("REQUIRED", scopeReason + " · 强制密文", false)
                : new PlatformApiCatalogService.PolicyDecision("OPTIONAL", scopeReason + " · 兼容协商", false);
    }

    private boolean matchesPatternRule(String antPattern, String probeUri, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) {
                continue;
            }
            String trimmed = rule.trim();
            if (trimmed.equals(antPattern)
                    || pathMatcher.match(trimmed, probeUri)
                    || pathMatcher.match(trimmed, antPattern)) {
                return true;
            }
        }
        return false;
    }

    public TelemetryStats getTelemetryStats() {
        return new TelemetryStats(
                totalRequests.sum(),
                encryptedRequests.sum(),
                plaintextAllowed.sum(),
                rejectedPlaintext.sum(),
                rejectedReplay.sum(),
                decryptErrors.sum()
        );
    }

    private boolean isAlwaysBypassPath(String uri) {
        return uri.equals("/api/security/transport/policy")
                || uri.startsWith("/api/health/")
                || uri.startsWith("/api/ws")
                || uri.startsWith("/api/file/download")
                || uri.startsWith("/api/file/preview")
                || uri.startsWith("/api/file/upload")
                || uri.startsWith("/api/file/raw-upload")
                || uri.startsWith("/api/file/raw-download")
                || uri.equals("/api/iam/profile/avatar");
    }

    private boolean matchesAny(String uri, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && pathMatcher.match(pattern.trim(), uri)) {
                return true;
            }
        }
        return false;
    }

    private void indexControllerAnnotations() {
        if (handlerMapping == null) {
            return;
        }
        annotationRules.clear();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod hm = entry.getValue();
            TransportCrypto ann = hm.getMethodAnnotation(TransportCrypto.class);
            if (ann == null) {
                ann = hm.getBeanType().getAnnotation(TransportCrypto.class);
            }
            if (ann != null) {
                List<String> patterns = new ArrayList<>(entry.getKey().getPatternValues());
                if (!patterns.isEmpty()) {
                    annotationRules.add(new AnnotationRule(patterns, ann.value()));
                }
            }
        }
    }

    private TransportCryptoPolicySnapshot buildFromProperties(long version) {
        return new TransportCryptoPolicySnapshot(
                defaultProperties.isEnabled() ? defaultProperties.getMode() : TransportCryptoMode.OFF,
                defaultProperties.getScopeMode(),
                TransportCryptoPolicySnapshot.ALGORITHM_SUITE,
                defaultProperties.isEncryptRequest(),
                defaultProperties.isEncryptResponse(),
                defaultProperties.isEncryptQuery(),
                defaultProperties.isReplayProtection(),
                defaultProperties.getTimestampSkewSeconds(),
                Math.max(1, defaultProperties.getKeyRotationHours()),
                Math.max(1, defaultProperties.getMaxGraceKeys()),
                cleanPatterns(defaultProperties.getIncludePaths(), List.of("/api/**")),
                mergeMandatoryExcludes(defaultProperties.getExcludePaths()),
                cleanPatterns(defaultProperties.getSensitivePaths(), List.of("/api/auth/login")),
                version,
                Instant.now(),
                "默认初始化传输加密策略"
        );
    }

    private List<String> cleanPatterns(List<String> raw, List<String> fallback) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        List<String> cleaned = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? new ArrayList<>(fallback) : cleaned;
    }

    private List<String> mergeMandatoryExcludes(List<String> configured) {
        List<String> merged = new ArrayList<>();
        if (configured != null) {
            for (String p : configured) {
                if (p != null && !p.isBlank() && !merged.contains(p.trim())) {
                    merged.add(p.trim());
                }
            }
        }
        for (String mandatory : List.of("/api/security/transport/policy", "/api/health/**")) {
            if (!merged.contains(mandatory)) {
                merged.add(mandatory);
            }
        }
        return merged;
    }

    private void syncFromRedisIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRedisSyncMillis < 1500L) {
            return;
        }
        lastRedisSyncMillis = now;
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        if (redis == null || mapper == null) {
            return;
        }
        try {
            String raw = redis.opsForValue().get(REDIS_POLICY_KEY);
            if (raw != null && !raw.isBlank()) {
                TransportCryptoPolicySnapshot remote = mapper.readValue(raw, TransportCryptoPolicySnapshot.class);
                if (remote != null && remote.policyVersion() > this.currentPolicy.policyVersion()) {
                    this.currentPolicy = remote;
                }
            }
        } catch (Exception ignored) {
            // Redis 短暂故障时维持内存当前策略版本
        }
    }

    private void cacheToRedis(TransportCryptoPolicySnapshot policy) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        ObjectMapper mapper = objectMapperProvider.getIfAvailable();
        if (redis == null || mapper == null) {
            return;
        }
        try {
            redis.opsForValue().set(REDIS_POLICY_KEY, mapper.writeValueAsString(policy));
        } catch (Exception ignored) {
            // 忽略非关键缓存异常
        }
    }
}
