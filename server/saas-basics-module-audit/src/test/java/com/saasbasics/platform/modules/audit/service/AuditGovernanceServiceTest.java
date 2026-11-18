package com.saasbasics.platform.modules.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateRequest;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateResponse;
import com.saasbasics.platform.modules.audit.entity.RiskRuleEntity;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import com.saasbasics.platform.modules.audit.mapper.SecBlacklistMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditGovernanceServiceTest {

    @Test
    void enforcesActiveBlacklistEntriesAcrossIpAndUser() {
        LoginLogMapper loginMapper = mock(LoginLogMapper.class);
        OperationLogMapper opMapper = mock(OperationLogMapper.class);
        RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
        SecBlacklistMapper blacklistMapper = mock(SecBlacklistMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuditTrailService auditTrail = mock(AuditTrailService.class);

        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM sys_config")))
                .thenReturn(List.of(Map.of("config_value", "{\"blacklistEnabled\":true}")));
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM sec_blacklist")))
                .thenReturn(List.of(
                        Map.of("tenant_id", 1L, "black_type", "IP", "black_value", "10.9.8.7", "reason", "暴力破解阻断"),
                        Map.of("tenant_id", 2L, "black_type", "USER", "black_value", "locked_actor", "reason", "越权尝试")
                ));

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("l", loginMapper);
        bf.addBean("o", opMapper);
        bf.addBean("r", ruleMapper);
        bf.addBean("b", blacklistMapper);
        bf.addBean("j", jdbcTemplate);
        bf.addBean("a", auditTrail);

        AuditService service = new AuditService(
                bf.getBeanProvider(LoginLogMapper.class),
                bf.getBeanProvider(OperationLogMapper.class),
                bf.getBeanProvider(RiskRuleMapper.class),
                bf.getBeanProvider(SecBlacklistMapper.class),
                bf.getBeanProvider(JdbcTemplate.class),
                bf.getBeanProvider(AuditTrailService.class)
        );

        assertThatThrownBy(() -> service.assertAllowed(2L, "alice", "10.9.8.7"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暴力破解阻断");

        assertThatThrownBy(() -> service.assertAllowed(2L, "locked_actor", "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("越权尝试");

        // Different tenant user/IP not matching rules should pass
        service.assertAllowed(3L, "locked_actor", "127.0.0.1");
    }

    @Test
    void evaluatesRiskRuleUsingDbMetricsAndThreshold() {
        LoginLogMapper loginMapper = mock(LoginLogMapper.class);
        OperationLogMapper opMapper = mock(OperationLogMapper.class);
        RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
        SecBlacklistMapper blacklistMapper = mock(SecBlacklistMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        RiskRuleEntity rule = new RiskRuleEntity();
        rule.setId(1L);
        rule.setTenantId(1L);
        rule.setRuleCode("RULE_BRUTE_FORCE");
        rule.setRuleName("暴力破解防范规则");
        rule.setRiskType("HIGH");
        rule.setStatus("ENABLED");
        rule.setActionJson("{\"action\":\"LOCK_ACCOUNT\",\"threshold\":5,\"windowMinutes\":5}");

        when(ruleMapper.selectOne(any())).thenReturn(rule);
        when(loginMapper.selectCount(any())).thenReturn(6L);
        when(opMapper.selectCount(any())).thenReturn(2L);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("l", loginMapper);
        bf.addBean("o", opMapper);
        bf.addBean("r", ruleMapper);
        bf.addBean("b", blacklistMapper);
        bf.addBean("j", jdbcTemplate);

        AuditService service = new AuditService(
                bf.getBeanProvider(LoginLogMapper.class),
                bf.getBeanProvider(OperationLogMapper.class),
                bf.getBeanProvider(RiskRuleMapper.class),
                bf.getBeanProvider(SecBlacklistMapper.class),
                bf.getBeanProvider(JdbcTemplate.class),
                bf.getBeanProvider(AuditTrailService.class)
        );

        RiskEvaluateResponse resp = service.evaluateRiskRule(new RiskEvaluateRequest("RULE_BRUTE_FORCE", "admin", "127.0.0.1", 1));
        assertThat(resp.decision()).isEqualTo("ALERT_AND_BLOCK");
        assertThat(resp.recentLoginFailures()).isEqualTo(6L);
    }
}
