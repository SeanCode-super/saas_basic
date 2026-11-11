package com.saasbasics.platform.telemetry;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 平台全量接口目录发现与多维度流量统计服务。
 * <p>
 * 能力覆盖：
 * 1. 基于 Spring {@link RequestMappingHandlerMapping} 自动扫描全量活跃接口及多级包名/控制器元数据；
 * 2. 以低基数模板路由为键实时统计各接口的总调用量、密文量、明文量、拦截量、错误量及平均耗时；
 * 3. 构建“项目级 -> 多级 Java 包名 -> Controller”与“URL 路径前缀”多维选择树，并生成无误伤的最简 Ant 通配规则。
 */
@Service
public class PlatformApiCatalogService implements SmartInitializingSingleton {

    private static final String BASE_PACKAGE = "com.saasbasics.platform";

    private static final Map<String, String> MODULE_LABELS = Map.ofEntries(
            Map.entry("ai", "AI 智能网关"),
            Map.entry("audit", "合规审计中心"),
            Map.entry("codegen", "低代码与代码生成"),
            Map.entry("crm", "客户关系管理 (CRM)"),
            Map.entry("dashboard", "控制台总览"),
            Map.entry("devops", "DevOps 发布中心"),
            Map.entry("erp", "企业资源计划 (ERP)"),
            Map.entry("file", "文件与对象存储"),
            Map.entry("health", "健康检查探针"),
            Map.entry("iam", "身份与权限 (IAM)"),
            Map.entry("integration", "开放集成与支付"),
            Map.entry("organization", "组织架构主数据"),
            Map.entry("scheduler", "定时任务调度"),
            Map.entry("security", "传输加密底座"),
            Map.entry("system", "系统配置与运维"),
            Map.entry("tenant", "多租户管理"),
            Map.entry("workflow", "工作流引擎")
    );

    public record PolicyDecision(String action, String reason, boolean alwaysBypass) {
    }

    public record RegisteredEndpoint(
            String endpointKey,
            String projectCode,
            String projectName,
            String packageName,
            List<String> packageLevels,
            String moduleCode,
            String moduleName,
            String controllerName,
            String handlerMethod,
            String httpMethod,
            String pathPattern,
            String antPattern,
            String urlPrefix2,
            String urlPrefix3
    ) {
    }

    public record ApiEndpointStatView(
            String endpointKey,
            String projectCode,
            String projectName,
            String packageName,
            List<String> packageLevels,
            String moduleCode,
            String moduleName,
            String controllerName,
            String handlerMethod,
            String httpMethod,
            String pathPattern,
            String antPattern,
            String urlPrefix2,
            String urlPrefix3,
            String effectiveAction,
            String matchedReason,
            boolean alwaysBypass,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls,
            long errorCalls,
            double avgLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            long lastCalledAtEpochMs
    ) {
    }

    public record EndpointStatDelta(
            String endpointKey,
            String httpMethod,
            String pathPattern,
            String moduleCode,
            long totalCallsDelta,
            long encryptedCallsDelta,
            long plaintextCallsDelta,
            long rejectedCallsDelta,
            long errorCallsDelta,
            long totalDurationMsDelta,
            double p95LatencyMs,
            double p99LatencyMs,
            long lastCalledAtEpochMs
    ) {
    }

    public record ApiPackageTreeNode(
            String id,
            String nodeType,
            String label,
            String fullName,
            int level,
            int endpointCount,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls,
            List<String> suggestedAntPatterns,
            List<ApiPackageTreeNode> children
    ) {
    }

    public record ApiPathGroupItem(
            String pattern,
            String label,
            int depth,
            int endpointCount,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls
    ) {
    }

    public record ApiModuleStatItem(
            String moduleCode,
            String moduleName,
            int endpointCount,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls,
            long errorCalls,
            List<String> suggestedAntPatterns
    ) {
    }

    public record ApiCatalogSummary(
            String projectCode,
            String projectName,
            int totalEndpoints,
            int activeEndpoints,
            int totalModules,
            int totalPackages,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls,
            long errorCalls
    ) {
    }

    public record ApiCatalogSnapshot(
            ApiCatalogSummary summary,
            List<ApiPackageTreeNode> packageTree,
            List<ApiPathGroupItem> pathGroups,
            List<ApiModuleStatItem> modules,
            List<ApiEndpointStatView> endpoints
    ) {
    }

    private static final class EndpointCounters {
        private static final int LATENCY_WINDOW = 512;

        final LongAdder totalCalls = new LongAdder();
        final LongAdder encryptedCalls = new LongAdder();
        final LongAdder plaintextCalls = new LongAdder();
        final LongAdder rejectedCalls = new LongAdder();
        final LongAdder errorCalls = new LongAdder();
        final LongAdder totalDurationMs = new LongAdder();
        final AtomicLong lastCalledAtEpochMs = new AtomicLong(0L);

        final AtomicLong syncedTotalCalls = new AtomicLong(0L);
        final AtomicLong syncedEncryptedCalls = new AtomicLong(0L);
        final AtomicLong syncedPlaintextCalls = new AtomicLong(0L);
        final AtomicLong syncedRejectedCalls = new AtomicLong(0L);
        final AtomicLong syncedErrorCalls = new AtomicLong(0L);
        final AtomicLong syncedTotalDurationMs = new AtomicLong(0L);

        private final long[] samples = new long[LATENCY_WINDOW];
        private int sampleCount = 0;
        private int sampleCursor = 0;
        volatile double hydratedP95Ms = 0.0;
        volatile double hydratedP99Ms = 0.0;

        synchronized void recordLatency(long durationMs) {
            samples[sampleCursor] = Math.max(0L, durationMs);
            sampleCursor = (sampleCursor + 1) % LATENCY_WINDOW;
            if (sampleCount < LATENCY_WINDOW) {
                sampleCount++;
            }
        }

        synchronized double[] computePercentiles() {
            if (sampleCount == 0) {
                return new double[]{hydratedP95Ms, hydratedP99Ms};
            }
            long[] copy = Arrays.copyOf(samples, sampleCount);
            Arrays.sort(copy);
            int p95Idx = Math.min(copy.length - 1, Math.max(0, (int) Math.ceil(copy.length * 0.95) - 1));
            int p99Idx = Math.min(copy.length - 1, Math.max(0, (int) Math.ceil(copy.length * 0.99) - 1));
            return new double[]{(double) copy[p95Idx], (double) copy[p99Idx]};
        }
    }

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider;
    private final String projectCode;
    private final String projectName;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private volatile List<RegisteredEndpoint> catalog = List.of();
    private final ConcurrentHashMap<String, List<RegisteredEndpoint>> byPatternIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointCounters> statsMap = new ConcurrentHashMap<>();

    public PlatformApiCatalogService(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider,
            Environment environment) {
        this.handlerMappingProvider = handlerMappingProvider;
        String appName = environment != null
                ? environment.getProperty("spring.application.name", "saas-basics-server")
                : "saas-basics-server";
        this.projectCode = appName;
        this.projectName = "主应用服务 (" + appName + ")";
    }

    @Override
    public void afterSingletonsInstantiated() {
        refreshCatalog();
    }

    /**
     * 扫描全量 Spring MVC 路由映射，建立多维度索引与计数器。
     */
    public synchronized void refreshCatalog() {
        RequestMappingHandlerMapping mapping = handlerMappingProvider.getIfAvailable();
        if (mapping == null) {
            return;
        }
        Map<String, RegisteredEndpoint> deduplicated = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            Set<String> patterns = entry.getKey().getPatternValues();
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            List<String> methodList = methods == null || methods.isEmpty()
                    ? List.of("ANY")
                    : methods.stream().map(Enum::name).sorted().toList();

            String packageName = handler.getBeanType().getPackageName();
            if (!packageName.startsWith("com.saasbasics")) {
                continue;
            }
            String controllerName = handler.getBeanType().getSimpleName();
            String methodName = handler.getMethod().getName();
            List<String> packageLevels = extractPackageLevels(packageName);
            String moduleCode = resolveModuleCode(packageName, patterns);
            String moduleName = MODULE_LABELS.getOrDefault(moduleCode, moduleCode);

            for (String rawPattern : patterns) {
                if (rawPattern == null || rawPattern.isBlank() || "/error".equals(rawPattern)) {
                    continue;
                }
                if (!rawPattern.startsWith("/api/") && !rawPattern.startsWith("/actuator")) {
                    continue;
                }
                String antPattern = normalizeAntPattern(rawPattern);
                String prefix2 = computeUrlPrefix(antPattern, 2);
                String prefix3 = computeUrlPrefix(antPattern, 3);

                for (String httpMethod : methodList) {
                    String key = httpMethod + " " + rawPattern;
                    RegisteredEndpoint item = new RegisteredEndpoint(
                            key,
                            projectCode,
                            projectName,
                            packageName,
                            packageLevels,
                            moduleCode,
                            moduleName,
                            controllerName,
                            methodName,
                            httpMethod,
                            rawPattern,
                            antPattern,
                            prefix2,
                            prefix3
                    );
                    deduplicated.put(key, item);
                    statsMap.computeIfAbsent(key, ignored -> new EndpointCounters());
                }
            }
        }

        List<RegisteredEndpoint> sorted = deduplicated.values().stream()
                .sorted(Comparator.comparing(RegisteredEndpoint::pathPattern)
                        .thenComparing(RegisteredEndpoint::httpMethod))
                .toList();

        Map<String, List<RegisteredEndpoint>> grouped = new HashMap<>();
        for (RegisteredEndpoint ep : sorted) {
            grouped.computeIfAbsent(ep.pathPattern(), ignored -> new ArrayList<>()).add(ep);
        }
        this.byPatternIndex.clear();
        this.byPatternIndex.putAll(grouped);
        this.catalog = sorted;
    }

    /**
     * 在 HTTP 请求完成阶段记录调用次数、状态错误与平均耗时（自动复用 Spring 匹配的模板路由，防止高基数溢出）。
     */
    public void recordHttpCall(HttpServletRequest request, int statusCode, long durationMs) {
        if (request == null) {
            return;
        }
        ensureCatalogInitialized();
        String method = normalizeMethod(request.getMethod());
        Object patternAttr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String matchedPattern = patternAttr != null ? patternAttr.toString() : resolvePatternForUri(method, request.getRequestURI());
        if (matchedPattern == null) {
            return;
        }
        String key = resolveStatsKey(method, matchedPattern);
        if (key == null) {
            return;
        }
        EndpointCounters counters = statsMap.computeIfAbsent(key, ignored -> new EndpointCounters());
        counters.totalCalls.increment();
        long safeDuration = Math.max(0L, durationMs);
        counters.totalDurationMs.add(safeDuration);
        counters.recordLatency(safeDuration);
        if (statusCode >= 400) {
            counters.errorCalls.increment();
        }
        counters.lastCalledAtEpochMs.set(System.currentTimeMillis());
    }

    /**
     * 在传输加密过滤器阶段按实际接口模板累加密文/明文/拦截统计。
     */
    public void recordTransportOutcome(String method, String requestUri, String outcome) {
        if (requestUri == null || outcome == null) {
            return;
        }
        ensureCatalogInitialized();
        String normMethod = normalizeMethod(method);
        String matchedPattern = resolvePatternForUri(normMethod, requestUri);
        if (matchedPattern == null) {
            return;
        }
        String key = resolveStatsKey(normMethod, matchedPattern);
        if (key == null) {
            return;
        }
        EndpointCounters counters = statsMap.computeIfAbsent(key, ignored -> new EndpointCounters());
        switch (outcome) {
            case "encrypted" -> counters.encryptedCalls.increment();
            case "plaintext" -> counters.plaintextCalls.increment();
            case "rejected_plaintext", "rejected_replay", "decrypt_error" -> {
                counters.rejectedCalls.increment();
                // 被传输过滤器提前拦截的请求不一定进入 DispatcherServlet，补记调用与耗时基线
                counters.totalCalls.increment();
                counters.errorCalls.increment();
                counters.lastCalledAtEpochMs.set(System.currentTimeMillis());
            }
            default -> {
            }
        }
    }

    /**
     * 构建包含实时统计、项目/多级包名选择树、路径层级与接口明细的全量快照。
     */
    public ApiCatalogSnapshot buildSnapshot(Function<String, PolicyDecision> policyEvaluator) {
        ensureCatalogInitialized();
        Function<String, PolicyDecision> evaluator = policyEvaluator != null
                ? policyEvaluator
                : pattern -> new PolicyDecision("OPTIONAL", "默认兼容模式", false);

        List<RegisteredEndpoint> allRegistered = this.catalog;
        List<String> allAntPatterns = allRegistered.stream()
                .map(RegisteredEndpoint::antPattern)
                .distinct()
                .toList();

        List<ApiEndpointStatView> views = new ArrayList<>(allRegistered.size());
        long sumCalls = 0L;
        long sumEnc = 0L;
        long sumPlain = 0L;
        long sumRej = 0L;
        long sumErr = 0L;
        int activeCount = 0;

        for (RegisteredEndpoint ep : allRegistered) {
            EndpointCounters c = statsMap.getOrDefault(ep.endpointKey(), new EndpointCounters());
            long calls = c.totalCalls.sum();
            long enc = c.encryptedCalls.sum();
            long plain = c.plaintextCalls.sum();
            long rej = c.rejectedCalls.sum();
            long err = c.errorCalls.sum();
            double avgMs = calls > 0 ? Math.round((c.totalDurationMs.sum() * 100.0) / calls) / 100.0 : 0.0;
            double[] pct = c.computePercentiles();
            PolicyDecision decision = evaluator.apply(ep.antPattern());
            if (calls > 0) {
                activeCount++;
            }
            sumCalls += calls;
            sumEnc += enc;
            sumPlain += plain;
            sumRej += rej;
            sumErr += err;

            views.add(new ApiEndpointStatView(
                    ep.endpointKey(),
                    ep.projectCode(),
                    ep.projectName(),
                    ep.packageName(),
                    ep.packageLevels(),
                    ep.moduleCode(),
                    ep.moduleName(),
                    ep.controllerName(),
                    ep.handlerMethod(),
                    ep.httpMethod(),
                    ep.pathPattern(),
                    ep.antPattern(),
                    ep.urlPrefix2(),
                    ep.urlPrefix3(),
                    decision.action(),
                    decision.reason(),
                    decision.alwaysBypass(),
                    calls,
                    enc,
                    plain,
                    rej,
                    err,
                    avgMs,
                    pct[0],
                    pct[1],
                    c.lastCalledAtEpochMs.get()
            ));
        }

        List<ApiPackageTreeNode> tree = buildPackageTree(views, allAntPatterns);
        List<ApiPathGroupItem> pathGroups = buildPathGroups(views);
        List<ApiModuleStatItem> modules = buildModuleStats(views, allAntPatterns);
        Set<String> distinctPackages = new HashSet<>();
        for (RegisteredEndpoint ep : allRegistered) {
            distinctPackages.add(ep.packageName());
        }

        ApiCatalogSummary summary = new ApiCatalogSummary(
                projectCode,
                projectName,
                views.size(),
                activeCount,
                modules.size(),
                distinctPackages.size(),
                sumCalls,
                sumEnc,
                sumPlain,
                sumRej,
                sumErr
        );
        return new ApiCatalogSnapshot(summary, tree, pathGroups, modules, views);
    }

    public String resolvePatternForUri(String method, String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String normalizedUri = stripQuery(uri);
        List<RegisteredEndpoint> exact = byPatternIndex.get(normalizedUri);
        if (exact != null && matchesMethodOrAny(exact, method)) {
            return normalizedUri;
        }
        String bestPattern = null;
        for (RegisteredEndpoint ep : catalog) {
            if (!methodMatches(ep.httpMethod(), method)) {
                continue;
            }
            if (pathMatcher.match(ep.pathPattern(), normalizedUri)) {
                if (bestPattern == null
                        || pathMatcher.getPatternComparator(normalizedUri).compare(ep.pathPattern(), bestPattern) < 0) {
                    bestPattern = ep.pathPattern();
                }
            }
        }
        return bestPattern;
    }

    private String resolveStatsKey(String method, String matchedPattern) {
        String direct = method + " " + matchedPattern;
        if (statsMap.containsKey(direct)) {
            return direct;
        }
        List<RegisteredEndpoint> list = byPatternIndex.get(matchedPattern);
        if (list != null) {
            for (RegisteredEndpoint ep : list) {
                if ("ANY".equals(ep.httpMethod())) {
                    return ep.endpointKey();
                }
            }
            if (!list.isEmpty()) {
                return list.get(0).endpointKey();
            }
        }
        return null;
    }

    private void ensureCatalogInitialized() {
        if (catalog.isEmpty() && handlerMappingProvider.getIfAvailable() != null) {
            refreshCatalog();
        }
    }

    private static final class MutableTreeNode {
        final String id;
        final String nodeType;
        final String label;
        final String fullName;
        final int level;
        final List<ApiEndpointStatView> endpoints = new ArrayList<>();
        final Map<String, MutableTreeNode> children = new TreeMap<>();

        MutableTreeNode(String id, String nodeType, String label, String fullName, int level) {
            this.id = id;
            this.nodeType = nodeType;
            this.label = label;
            this.fullName = fullName;
            this.level = level;
        }
    }

    private List<ApiPackageTreeNode> buildPackageTree(List<ApiEndpointStatView> views, List<String> allAntPatterns) {
        MutableTreeNode root = new MutableTreeNode(
                "project:" + projectCode,
                "PROJECT",
                projectName,
                BASE_PACKAGE,
                0
        );

        for (ApiEndpointStatView ep : views) {
            root.endpoints.add(ep);
            MutableTreeNode cursor = root;
            StringBuilder pkgAcc = new StringBuilder(BASE_PACKAGE);
            List<String> levels = ep.packageLevels();
            for (int i = 0; i < levels.size(); i++) {
                final int nodeLevel = i + 1;
                String seg = levels.get(i);
                pkgAcc.append('.').append(seg);
                String fullPkg = pkgAcc.toString();
                String childKey = "pkg:" + fullPkg;
                String display = formatPackageSegmentLabel(levels, i, seg);
                MutableTreeNode next = cursor.children.computeIfAbsent(
                        childKey,
                        ignored -> new MutableTreeNode(childKey, "PACKAGE", display, fullPkg, nodeLevel)
                );
                next.endpoints.add(ep);
                cursor = next;
            }
            String ctrlFull = ep.packageName() + "." + ep.controllerName();
            String ctrlKey = "ctrl:" + ctrlFull;
            MutableTreeNode ctrlNode = cursor.children.computeIfAbsent(
                    ctrlKey,
                    ignored -> new MutableTreeNode(ctrlKey, "CONTROLLER", ep.controllerName(), ctrlFull, levels.size() + 1)
            );
            ctrlNode.endpoints.add(ep);
        }

        return List.of(freezeTreeNode(root, allAntPatterns));
    }

    private ApiPackageTreeNode freezeTreeNode(MutableTreeNode node, List<String> allAntPatterns) {
        long calls = 0L;
        long enc = 0L;
        long plain = 0L;
        long rej = 0L;
        Set<String> selectedAnts = new LinkedHashSet<>();
        for (ApiEndpointStatView ep : node.endpoints) {
            calls += ep.totalCalls();
            enc += ep.encryptedCalls();
            plain += ep.plaintextCalls();
            rej += ep.rejectedCalls();
            selectedAnts.add(ep.antPattern());
        }

        List<ApiPackageTreeNode> frozenChildren = new ArrayList<>();
        for (MutableTreeNode child : node.children.values()) {
            frozenChildren.add(freezeTreeNode(child, allAntPatterns));
        }
        frozenChildren.sort(Comparator.comparing(ApiPackageTreeNode::nodeType)
                .thenComparing(ApiPackageTreeNode::label));

        List<String> suggested = "PROJECT".equals(node.nodeType)
                ? List.of("/api/**")
                : computeSafeAntPatterns(new ArrayList<>(selectedAnts), allAntPatterns);

        return new ApiPackageTreeNode(
                node.id,
                node.nodeType,
                node.label,
                node.fullName,
                node.level,
                node.endpoints.size(),
                calls,
                enc,
                plain,
                rej,
                suggested,
                frozenChildren
        );
    }

    private List<ApiPathGroupItem> buildPathGroups(List<ApiEndpointStatView> views) {
        Map<String, List<ApiEndpointStatView>> grouped = new LinkedHashMap<>();
        grouped.put("/api/**", new ArrayList<>(views));
        for (ApiEndpointStatView ep : views) {
            if (ep.urlPrefix2() != null && !ep.urlPrefix2().isBlank()) {
                grouped.computeIfAbsent(ep.urlPrefix2(), ignored -> new ArrayList<>()).add(ep);
            }
            if (ep.urlPrefix3() != null && !ep.urlPrefix3().isBlank() && !ep.urlPrefix3().equals(ep.urlPrefix2())) {
                grouped.computeIfAbsent(ep.urlPrefix3(), ignored -> new ArrayList<>()).add(ep);
            }
        }
        List<ApiPathGroupItem> list = new ArrayList<>();
        for (Map.Entry<String, List<ApiEndpointStatView>> entry : grouped.entrySet()) {
            String pattern = entry.getKey();
            List<ApiEndpointStatView> eps = entry.getValue();
            long calls = 0L;
            long enc = 0L;
            long plain = 0L;
            long rej = 0L;
            for (ApiEndpointStatView ep : eps) {
                calls += ep.totalCalls();
                enc += ep.encryptedCalls();
                plain += ep.plaintextCalls();
                rej += ep.rejectedCalls();
            }
            int depth = "/api/**".equals(pattern) ? 1 : (int) pattern.chars().filter(ch -> ch == '/').count() - 1;
            list.add(new ApiPathGroupItem(pattern, pattern, depth, eps.size(), calls, enc, plain, rej));
        }
        list.sort(Comparator.comparingInt(ApiPathGroupItem::depth).thenComparing(ApiPathGroupItem::pattern));
        return list;
    }

    private List<ApiModuleStatItem> buildModuleStats(List<ApiEndpointStatView> views, List<String> allAntPatterns) {
        Map<String, List<ApiEndpointStatView>> byMod = new LinkedHashMap<>();
        for (ApiEndpointStatView ep : views) {
            byMod.computeIfAbsent(ep.moduleCode(), ignored -> new ArrayList<>()).add(ep);
        }
        List<ApiModuleStatItem> result = new ArrayList<>();
        for (Map.Entry<String, List<ApiEndpointStatView>> entry : byMod.entrySet()) {
            List<ApiEndpointStatView> eps = entry.getValue();
            long calls = 0L;
            long enc = 0L;
            long plain = 0L;
            long rej = 0L;
            long err = 0L;
            Set<String> selectedAnts = new LinkedHashSet<>();
            for (ApiEndpointStatView ep : eps) {
                calls += ep.totalCalls();
                enc += ep.encryptedCalls();
                plain += ep.plaintextCalls();
                rej += ep.rejectedCalls();
                err += ep.errorCalls();
                selectedAnts.add(ep.antPattern());
            }
            List<String> suggested = computeSafeAntPatterns(new ArrayList<>(selectedAnts), allAntPatterns);
            result.add(new ApiModuleStatItem(
                    entry.getKey(),
                    eps.get(0).moduleName(),
                    eps.size(),
                    calls,
                    enc,
                    plain,
                    rej,
                    err,
                    suggested
            ));
        }
        result.sort(Comparator.comparing(ApiModuleStatItem::endpointCount).reversed()
                .thenComparing(ApiModuleStatItem::moduleCode));
        return result;
    }

    /**
     * 基于全量接口集合计算无副作用（不会误伤未选中节点）的最简 Ant 通配路径规则集合。
     */
    public List<String> computeSafeAntPatterns(List<String> selectedPatterns, List<String> allPatterns) {
        if (selectedPatterns == null || selectedPatterns.isEmpty()) {
            return List.of();
        }
        Set<String> selectedSet = new LinkedHashSet<>(selectedPatterns);
        Set<String> outsideSet = new HashSet<>(allPatterns);
        outsideSet.removeAll(selectedSet);

        if (outsideSet.isEmpty() && selectedSet.stream().allMatch(p -> p.startsWith("/api/"))) {
            return List.of("/api/**");
        }

        List<String> safePrefixes = new ArrayList<>();
        Set<String> covered = new HashSet<>();

        for (String p : selectedSet) {
            String broadestSafe = findBroadestSafePrefix(p, outsideSet);
            if (broadestSafe != null && !safePrefixes.contains(broadestSafe)) {
                // 统计该前缀能覆盖几个选中的端点，避免仅匹配1个参数化路径生成无意义前缀
                int hitSelected = 0;
                for (String s : selectedSet) {
                    if (pathMatcher.match(broadestSafe, s)) {
                        hitSelected++;
                    }
                }
                if (hitSelected > 1 || (hitSelected == 1 && !p.equals(broadestSafe.replace("/**", "")))) {
                    safePrefixes.add(broadestSafe);
                }
            }
        }

        // 去重被更宽泛前缀覆盖的子前缀
        List<String> minimalPrefixes = new ArrayList<>();
        safePrefixes.sort(Comparator.comparingInt(String::length));
        for (String pref : safePrefixes) {
            boolean alreadyCovered = minimalPrefixes.stream().anyMatch(existing -> pathMatcher.match(existing, pref.replace("/**", "/probe")));
            if (!alreadyCovered) {
                minimalPrefixes.add(pref);
            }
        }

        List<String> output = new ArrayList<>(minimalPrefixes);
        for (String p : selectedSet) {
            boolean coveredByPrefix = minimalPrefixes.stream().anyMatch(pref -> pathMatcher.match(pref, p));
            if (!coveredByPrefix) {
                output.add(p);
            } else {
                covered.add(p);
            }
        }
        return output.stream().distinct().toList();
    }

    private String findBroadestSafePrefix(String pattern, Set<String> outsideSet) {
        if (pattern == null || !pattern.startsWith("/api/")) {
            return null;
        }
        String clean = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        String[] segments = clean.split("/");
        // 至少从 2 级（/api/<module>/**）开始试探，避免返回过宽的 /api/**
        for (int depth = 2; depth <= segments.length; depth++) {
            StringBuilder sb = new StringBuilder();
            boolean validPrefix = true;
            for (int i = 0; i < depth; i++) {
                if ("*".equals(segments[i]) || "**".equals(segments[i]) || segments[i].contains("{")) {
                    validPrefix = false;
                    break;
                }
                sb.append('/').append(segments[i]);
            }
            if (!validPrefix) {
                continue;
            }
            String candidate = sb + "/**";
            boolean touchesOutside = false;
            for (String out : outsideSet) {
                if (pathMatcher.match(candidate, out)) {
                    touchesOutside = true;
                    break;
                }
            }
            if (!touchesOutside) {
                return candidate;
            }
        }
        return null;
    }

    static String normalizeAntPattern(String rawPattern) {
        if (rawPattern == null || rawPattern.isBlank()) {
            return "/";
        }
        StringBuilder out = new StringBuilder();
        int braceDepth = 0;
        for (int i = 0; i < rawPattern.length(); i++) {
            char ch = rawPattern.charAt(i);
            if (ch == '{') {
                if (braceDepth == 0) {
                    out.append('*');
                }
                braceDepth++;
            } else if (ch == '}') {
                if (braceDepth > 0) {
                    braceDepth--;
                }
            } else if (braceDepth == 0) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    public void hydrateBaselineStat(
            String endpointKey,
            long totalCalls,
            long encryptedCalls,
            long plaintextCalls,
            long rejectedCalls,
            long errorCalls,
            long totalDurationMs,
            double p95Ms,
            double p99Ms,
            long lastCalledAtMs) {
        if (endpointKey == null || endpointKey.isBlank()) {
            return;
        }
        EndpointCounters c = statsMap.computeIfAbsent(endpointKey, ignored -> new EndpointCounters());
        if (c.totalCalls.sum() == 0 && totalCalls > 0) {
            c.totalCalls.add(totalCalls);
            c.encryptedCalls.add(encryptedCalls);
            c.plaintextCalls.add(plaintextCalls);
            c.rejectedCalls.add(rejectedCalls);
            c.errorCalls.add(errorCalls);
            c.totalDurationMs.add(totalDurationMs);
            c.syncedTotalCalls.set(totalCalls);
            c.syncedEncryptedCalls.set(encryptedCalls);
            c.syncedPlaintextCalls.set(plaintextCalls);
            c.syncedRejectedCalls.set(rejectedCalls);
            c.syncedErrorCalls.set(errorCalls);
            c.syncedTotalDurationMs.set(totalDurationMs);
            c.hydratedP95Ms = p95Ms;
            c.hydratedP99Ms = p99Ms;
            c.lastCalledAtEpochMs.accumulateAndGet(lastCalledAtMs, Math::max);
        }
    }

    public List<EndpointStatDelta> drainUnsyncedDeltas() {
        ensureCatalogInitialized();
        List<EndpointStatDelta> deltas = new ArrayList<>();
        for (RegisteredEndpoint ep : this.catalog) {
            EndpointCounters c = statsMap.get(ep.endpointKey());
            if (c == null) {
                continue;
            }
            long curTotal = c.totalCalls.sum();
            long curEnc = c.encryptedCalls.sum();
            long curPlain = c.plaintextCalls.sum();
            long curRej = c.rejectedCalls.sum();
            long curErr = c.errorCalls.sum();
            long curDur = c.totalDurationMs.sum();

            long dTotal = Math.max(0L, curTotal - c.syncedTotalCalls.get());
            long dEnc = Math.max(0L, curEnc - c.syncedEncryptedCalls.get());
            long dPlain = Math.max(0L, curPlain - c.syncedPlaintextCalls.get());
            long dRej = Math.max(0L, curRej - c.syncedRejectedCalls.get());
            long dErr = Math.max(0L, curErr - c.syncedErrorCalls.get());
            long dDur = Math.max(0L, curDur - c.syncedTotalDurationMs.get());

            if (dTotal == 0 && dEnc == 0 && dPlain == 0 && dRej == 0 && dErr == 0) {
                continue;
            }

            c.syncedTotalCalls.addAndGet(dTotal);
            c.syncedEncryptedCalls.addAndGet(dEnc);
            c.syncedPlaintextCalls.addAndGet(dPlain);
            c.syncedRejectedCalls.addAndGet(dRej);
            c.syncedErrorCalls.addAndGet(dErr);
            c.syncedTotalDurationMs.addAndGet(dDur);

            double[] pct = c.computePercentiles();
            deltas.add(new EndpointStatDelta(
                    ep.endpointKey(),
                    ep.httpMethod(),
                    ep.pathPattern(),
                    ep.moduleCode(),
                    dTotal,
                    dEnc,
                    dPlain,
                    dRej,
                    dErr,
                    dDur,
                    pct[0],
                    pct[1],
                    c.lastCalledAtEpochMs.get()
            ));
        }
        return deltas;
    }

    private static List<String> extractPackageLevels(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return List.of("root");
        }
        String rel = packageName.startsWith(BASE_PACKAGE + ".")
                ? packageName.substring(BASE_PACKAGE.length() + 1)
                : packageName;
        String[] parts = rel.split("\\.");
        return List.of(parts);
    }

    private static String formatPackageSegmentLabel(List<String> levels, int index, String seg) {
        if (index == 0 && "modules".equals(seg)) {
            return "modules (业务模块层)";
        }
        if (index == 1 && levels.size() >= 2 && "modules".equals(levels.get(0))) {
            String cn = MODULE_LABELS.get(seg);
            return cn != null ? seg + " · " + cn : seg;
        }
        String cn = MODULE_LABELS.get(seg);
        return cn != null ? seg + " · " + cn : seg;
    }

    private static String resolveModuleCode(String packageName, Set<String> patterns) {
        if (packageName != null && packageName.startsWith(BASE_PACKAGE + ".modules.")) {
            String rest = packageName.substring((BASE_PACKAGE + ".modules.").length());
            String[] segs = rest.split("\\.");
            if (segs.length >= 2 && "system".equals(segs[0]) && "devops".equals(segs[1])) {
                return "devops";
            }
            if (segs.length >= 1 && !segs[0].isBlank()) {
                return segs[0];
            }
        }
        if (packageName != null && packageName.contains(".security.transport")) {
            return "security";
        }
        for (String p : patterns) {
            if (p.startsWith("/api/")) {
                String[] parts = p.substring(1).split("/");
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    return parts[1];
                }
            }
        }
        return "platform";
    }

    private static String computeUrlPrefix(String antPattern, int depth) {
        if (antPattern == null || !antPattern.startsWith("/")) {
            return "/api/**";
        }
        String clean = antPattern.substring(1);
        String[] parts = clean.split("/");
        if (parts.length <= depth) {
            return antPattern;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth && i < parts.length; i++) {
            if ("*".equals(parts[i]) || "**".equals(parts[i])) {
                break;
            }
            sb.append('/').append(parts[i]);
        }
        return sb.isEmpty() ? "/api/**" : sb + "/**";
    }

    private static boolean matchesMethodOrAny(List<RegisteredEndpoint> list, String method) {
        for (RegisteredEndpoint ep : list) {
            if (methodMatches(ep.httpMethod(), method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean methodMatches(String registeredMethod, String actualMethod) {
        return "ANY".equals(registeredMethod) || Objects.equals(registeredMethod, actualMethod);
    }

    private static String normalizeMethod(String method) {
        return (method == null || method.isBlank()) ? "GET" : method.trim().toUpperCase(Locale.ROOT);
    }

    private static String stripQuery(String uri) {
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }
}
