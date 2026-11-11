package com.saasbasics.platform.modules.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.ai.application.service.AiMeteringQuotaService;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiTenantQuotaEntity;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper.AiTenantQuotaMapper;
import com.saasbasics.platform.ratelimit.RateLimitProperties;
import com.saasbasics.platform.ratelimit.SlidingWindowRateLimiter;
import com.saasbasics.platform.security.vault.CredentialVaultService;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import com.saasbasics.platform.telemetry.TelemetryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AiMeteringQuotaServiceTest {

    @Test
    void enforcesMonthlyTokenBudgetAndRecordsPrometheusMetrics() {
        AiTenantQuotaMapper quotaMapper = mock(AiTenantQuotaMapper.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("meterRegistry", meterRegistry);
        PlatformTelemetryService telemetryService = new PlatformTelemetryService(
                new TelemetryProperties(),
                bf.getBeanProvider(MeterRegistry.class),
                bf.getBeanProvider(SlidingWindowRateLimiter.class),
                bf.getBeanProvider(RateLimitProperties.class),
                bf.getBeanProvider(CredentialVaultService.class)
        );
        bf.addBean("telemetry", telemetryService);

        AiMeteringQuotaService quotaService = new AiMeteringQuotaService(
                quotaMapper, bf.getBeanProvider(PlatformTelemetryService.class)
        );

        AiTenantQuotaEntity quota = new AiTenantQuotaEntity();
        quota.setTenantId(101L);
        quota.setMonthlyTokenLimit(1000L);
        quota.setUsedTokensMonth(800L);
        quota.setPromptTokensMonth(300L);
        quota.setCompletionTokensMonth(500L);
        quota.setEstimatedCostMonth(BigDecimal.ZERO);
        quota.setBillingMonth(YearMonth.now().toString());
        quota.setStatus("ENABLED");

        when(quotaMapper.selectOne(any())).thenReturn(quota);

        quotaService.checkQuota(101L); // 800 < 1000 passes
        quotaService.recordUsage(101L, "deepseek", "deepseek-chat", "SUCCEEDED", 100, 150, 420L);

        assertThat(quota.getUsedTokensMonth()).isEqualTo(1050L);
        assertThat(meterRegistry.get("saas_ai_requests_total").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("saas_ai_tokens_total").tag("type", "prompt").counter().count()).isEqualTo(100.0);
        assertThat(meterRegistry.get("saas_ai_tokens_total").tag("type", "completion").counter().count()).isEqualTo(150.0);

        // Next check must reject with AI_QUOTA_EXCEEDED
        assertThatThrownBy(() -> quotaService.checkQuota(101L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("AI_QUOTA_EXCEEDED");

        telemetryService.shutdown();
    }
}
