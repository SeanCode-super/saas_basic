package com.saasbasics.platform.modules.ai.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TelemetrySnapshot;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlatformMetricsSummaryTool implements AiToolHandler {

    private final PlatformTelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public PlatformMetricsSummaryTool(PlatformTelemetryService telemetryService, ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "platform.metrics.summary";
    }

    @Override
    public String toolName() {
        return "平台运行指标与健康概览";
    }

    @Override
    public String description() {
        return "只读检索当前底座 JVM 堆内存、限流统计与 OpenTelemetry 遥测摘要";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
        try {
            TelemetrySnapshot snap = telemetryService.getSnapshot();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("serviceName", snap.serviceName());
            payload.put("jvmHeapUsedMb", heap.getUsed() / (1024 * 1024));
            payload.put("jvmHeapMaxMb", heap.getMax() > 0 ? heap.getMax() / (1024 * 1024) : 0);
            payload.put("httpTotalRequests", snap.httpTotalRequests());
            payload.put("httpErrorRequests", snap.httpErrorRequests());
            payload.put("avgLatencyMs", snap.avgLatencyMs());
            payload.put("rateLimitAllowed", snap.rateLimitAllowed());
            payload.put("rateLimitRejected", snap.rateLimitRejected());
            payload.put("aiRequestsTotal", snap.aiRequestsTotal());
            return AiToolExecutionResult.ok(
                    "已采集中枢遥测：HTTP 请求 " + snap.httpTotalRequests() + " 次，均耗时 "
                            + snap.avgLatencyMs() + "ms，JVM 堆占用 " + payload.get("jvmHeapUsedMb") + " MB",
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            return AiToolExecutionResult.error("采集平台指标失败: " + e.getMessage());
        }
    }
}
