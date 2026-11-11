package com.saasbasics.platform.modules.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.modules.system.dto.AlertGovernanceDtos.AlertEvaluationSummary;
import com.saasbasics.platform.modules.system.entity.AlertEventEntity;
import com.saasbasics.platform.modules.system.entity.AlertRuleEntity;
import com.saasbasics.platform.modules.system.mapper.AlertEventMapper;
import com.saasbasics.platform.modules.system.mapper.AlertRuleMapper;
import com.saasbasics.platform.security.transport.TransportCryptoPolicyService;
import com.saasbasics.platform.telemetry.PlatformApiCatalogService;
import com.saasbasics.platform.telemetry.PlatformTelemetryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AlertGovernanceServiceTest {

    @Test
    void triggersAlertEventAndInAppNotificationWhenThresholdExceeded() {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertEventMapper eventMapper = mock(AlertEventMapper.class);
        SystemNotifyService notifyService = mock(SystemNotifyService.class);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("notify", notifyService);

        AlertRuleEntity rule = new AlertRuleEntity();
        rule.setId(1L);
        rule.setRuleCode("JVM_TEST");
        rule.setRuleName("JVM Test Rule");
        rule.setMetricType("JVM_HEAP_PERCENT");
        rule.setComparisonOp("GTE");
        rule.setThresholdValue(0.01);
        rule.setSeverity("CRITICAL");
        rule.setCooldownSeconds(300);
        rule.setNotifyChannels("IN_APP");
        rule.setEnabled(true);

        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        AlertGovernanceService service = new AlertGovernanceService(
                ruleMapper,
                eventMapper,
                bf.getBeanProvider(PlatformApiCatalogService.class),
                bf.getBeanProvider(PlatformTelemetryService.class),
                bf.getBeanProvider(TransportCryptoPolicyService.class),
                bf.getBeanProvider(SystemNotifyService.class),
                bf.getBeanProvider(StringRedisTemplate.class),
                new ObjectMapper()
        );

        AlertEvaluationSummary summary = service.evaluateAllRules(true);

        assertThat(summary.evaluatedRules()).isEqualTo(1);
        assertThat(summary.triggeredEvents()).isEqualTo(1);
        assertThat(summary.newEvents().get(0).ruleCode()).isEqualTo("JVM_TEST");
        verify(eventMapper).insert(any(AlertEventEntity.class));
        verify(notifyService).createAndSend(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("ALERT"),
                any(),
                any(),
                any(),
                any()
        );
    }
}
