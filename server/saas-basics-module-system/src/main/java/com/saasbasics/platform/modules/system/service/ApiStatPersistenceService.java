package com.saasbasics.platform.modules.system.service;

import com.saasbasics.platform.modules.system.entity.ApiEndpointStatEntity;
import com.saasbasics.platform.modules.system.mapper.ApiEndpointStatMapper;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService.EndpointStatDelta;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 全量接口调用与 P95/P99 时延持久化服务：
 * 启动时从 MySQL 加载历史基线，运行期定期将各节点增量 Delta 原子合并至 MySQL 并同步 Redis 集群聚合视图。
 */
@Service
public class ApiStatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ApiStatPersistenceService.class);
    private static final String REDIS_API_STATS_KEY = "saas:telemetry:api-stats:summary";

    private final ObjectProvider<PlatformApiCatalogService> catalogProvider;
    private final ObjectProvider<ApiEndpointStatMapper> statMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public ApiStatPersistenceService(
            ObjectProvider<PlatformApiCatalogService> catalogProvider,
            ObjectProvider<ApiEndpointStatMapper> statMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.catalogProvider = catalogProvider;
        this.statMapperProvider = statMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrateOnStartup() {
        PlatformApiCatalogService catalog = catalogProvider.getIfAvailable();
        ApiEndpointStatMapper mapper = statMapperProvider.getIfAvailable();
        if (catalog == null || mapper == null) {
            return;
        }
        try {
            List<ApiEndpointStatEntity> rows = mapper.selectList(null);
            for (ApiEndpointStatEntity row : rows) {
                catalog.hydrateBaselineStat(
                        row.getEndpointKey(),
                        safeLong(row.getTotalCalls()),
                        safeLong(row.getEncryptedCalls()),
                        safeLong(row.getPlaintextCalls()),
                        safeLong(row.getRejectedCalls()),
                        safeLong(row.getErrorCalls()),
                        safeLong(row.getTotalDurationMs()),
                        safeDouble(row.getP95LatencyMs()),
                        safeDouble(row.getP99LatencyMs()),
                        safeLong(row.getLastCalledAtMs())
                );
            }
            log.info("[Telemetry] 已从数据库回填 {} 条接口历史监控基线", rows.size());
        } catch (Exception ex) {
            log.debug("[Telemetry] 接口统计基线加载跳过: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 20_000L)
    public void scheduledFlush() {
        flushNow();
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushNow();
    }

    public int flushNow() {
        PlatformApiCatalogService catalog = catalogProvider.getIfAvailable();
        ApiEndpointStatMapper mapper = statMapperProvider.getIfAvailable();
        if (catalog == null || mapper == null) {
            return 0;
        }
        List<EndpointStatDelta> deltas = catalog.drainUnsyncedDeltas();
        int flushed = 0;
        for (EndpointStatDelta delta : deltas) {
            try {
                mapper.accumulateDelta(
                        delta.endpointKey(),
                        delta.httpMethod(),
                        delta.pathPattern(),
                        delta.moduleCode(),
                        delta.totalCallsDelta(),
                        delta.encryptedCallsDelta(),
                        delta.plaintextCallsDelta(),
                        delta.rejectedCallsDelta(),
                        delta.errorCallsDelta(),
                        delta.totalDurationMsDelta(),
                        delta.p95LatencyMs(),
                        delta.p99LatencyMs(),
                        delta.lastCalledAtEpochMs()
                );
                syncRedisClusterHash(delta);
                flushed++;
            } catch (Exception ex) {
                log.warn("[Telemetry] 接口增量统计落库失败 {}: {}", delta.endpointKey(), ex.getMessage());
            }
        }
        return flushed;
    }

    private void syncRedisClusterHash(EndpointStatDelta delta) {
        StringRedisTemplate redis = redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
        if (redis == null || delta.totalCallsDelta() <= 0) {
            return;
        }
        try {
            redis.opsForHash().increment(REDIS_API_STATS_KEY, delta.endpointKey(), delta.totalCallsDelta());
        } catch (Exception ignored) {
            // Best-effort multi-node Redis rollup
        }
    }

    private static long safeLong(Long val) {
        return val != null ? val : 0L;
    }

    private static double safeDouble(Double val) {
        return val != null ? val : 0.0;
    }
}
