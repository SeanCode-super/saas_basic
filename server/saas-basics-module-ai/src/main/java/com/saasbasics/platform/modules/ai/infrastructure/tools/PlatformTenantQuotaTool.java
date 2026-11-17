package com.saasbasics.platform.modules.ai.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.ai.api.AiToolExecutionResult;
import com.saasbasics.platform.modules.ai.api.AiToolHandler;
import com.saasbasics.platform.modules.ai.api.ToolRiskLevel;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiTenantQuotaEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiTenantQuotaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlatformTenantQuotaTool implements AiToolHandler {

    private final AiTenantQuotaMapper quotaMapper;
    private final ObjectMapper objectMapper;

    public PlatformTenantQuotaTool(AiTenantQuotaMapper quotaMapper, ObjectMapper objectMapper) {
        this.quotaMapper = quotaMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "platform.tenant.quota";
    }

    @Override
    public String toolName() {
        return "查询租户 AI Token 配额账单";
    }

    @Override
    public String description() {
        return "只读获取当前登录租户的 AI 月度 Token 额度、消耗进度与 RPM 上限";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.READ_ONLY;
    }

    @Override
    public AiToolExecutionResult execute(Long tenantId, Long userId, Map<String, Object> arguments) {
        try {
            long targetTenant = tenantId != null ? tenantId : 1L;
            AiTenantQuotaEntity quota = quotaMapper.selectOne(
                    new LambdaQueryWrapper<AiTenantQuotaEntity>()
                            .eq(AiTenantQuotaEntity::getTenantId, targetTenant)
                            .last("LIMIT 1")
            );
            long limit = quota != null ? quota.getMonthlyTokenLimit() : 5_000_000L;
            long used = quota != null && quota.getUsedTokensMonth() != null ? quota.getUsedTokensMonth() : 0L;
            int rpm = quota != null && quota.getRpmLimit() != null ? quota.getRpmLimit() : 60;
            Map<String, Object> payload = Map.of(
                    "tenantId", targetTenant,
                    "monthlyLimit", limit,
                    "usedTokens", used,
                    "remainingTokens", Math.max(0L, limit - used),
                    "rpmLimit", rpm
            );
            return AiToolExecutionResult.ok(
                    "租户 #" + targetTenant + " 月度 Token 已消耗 " + used + " / " + limit + "（RPM 上限 " + rpm + "）",
                    objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            return AiToolExecutionResult.error("读取租户配额失败: " + e.getMessage());
        }
    }
}
