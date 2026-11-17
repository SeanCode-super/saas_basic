package com.saasbasics.platform.modules.ai.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TraceRecord;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PlatformAuditRecentTool implements AiToolHandler {

    private final PlatformTelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public PlatformAuditRecentTool(PlatformTelemetryService telemetryService, ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "platform.audit.recent";
    }

    @Override
    public String toolName() {
        return "检索近期平台审计追踪摘要";
    }

    @Override
    public String description() {
        return "按当前租户安全上下文查询近期 OpenTelemetry 链路追踪摘要（严格隔离跨租户记录）";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
        try {
            int limit = 5;
            if (arguments != null && arguments.get("limit") instanceof Number n) {
                limit = Math.max(1, Math.min(20, n.intValue()));
            }
            Long targetTenant = tenantId != null ? tenantId : 1L;
            List<TraceRecord> filtered = telemetryService.getSnapshot().recentTraces().stream()
                    .filter(r -> r.tenantId() == null || Objects.equals(r.tenantId(), targetTenant))
                    .limit(limit)
                    .toList();
            return AiToolExecutionResult.ok(
                    "检索到 " + filtered.size() + " 条属于当前租户的近期 Trace 记录",
                    objectMapper.writeValueAsString(Map.of("traces", filtered))
            );
        } catch (Exception e) {
            return AiToolExecutionResult.error("查询追踪记录失败: " + e.getMessage());
        }
    }
}
