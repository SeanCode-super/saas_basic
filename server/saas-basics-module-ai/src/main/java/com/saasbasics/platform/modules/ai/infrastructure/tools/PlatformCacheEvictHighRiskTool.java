package com.saasbasics.platform.modules.ai.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.ratelimit.SlidingWindowRateLimiter;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 演示 M3 高危写操作人工审批风控门禁的工具。
 */
@Component
public class PlatformCacheEvictHighRiskTool implements AiToolHandler {

    private final SlidingWindowRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public PlatformCacheEvictHighRiskTool(SlidingWindowRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "platform.cache.evict";
    }

    @Override
    public String toolName() {
        return "清理租户平台级运行缓存 (高危写操作)";
    }

    @Override
    public String description() {
        return "高风险运维写入工具：清理本地限流滑动窗口缓存，必须经过人工审批及幂等键校验方可执行";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.HIGH;
    }

    @Override
    public boolean idempotent() {
        return true;
    }

    @Override
    public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
        try {
            rateLimiter.clearLocalWindows();
            String scope = arguments != null && arguments.get("scope") != null
                    ? String.valueOf(arguments.get("scope"))
                    : "rate_limit";
            Map<String, Object> res = Map.of(
                    "evicted", true,
                    "scope", scope,
                    "tenantId", tenantId != null ? tenantId : 1L,
                    "executedByUserId", userId != null ? userId : 0L,
                    "timestamp", Instant.now().toString()
            );
            return AiToolExecutionResult.ok(
                    "人工审批放行后已清理范围 [" + scope + "] 缓存（租户 #" + tenantId + "）",
                    objectMapper.writeValueAsString(res)
            );
        } catch (Exception e) {
            return AiToolExecutionResult.error("缓存清理失败: " + e.getMessage());
        }
    }
}
