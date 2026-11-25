package com.saasbasics.platform.modules.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateRequest;
import com.saasbasics.platform.modules.audit.dto.RiskEvaluateResponse;
import com.saasbasics.platform.modules.audit.entity.RiskRuleEntity;
import com.saasbasics.platform.modules.audit.mapper.AuditQueryMapper;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import com.saasbasics.platform.modules.audit.mapper.SecBlacklistMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AuditGovernanceServiceTest {

    @Test
    void distinguishesMissingAuditMapperFromAnEmptyHighRiskCatalog() {
        var beans = new StaticListableBeanFactory();
        AuditService service = new AuditService(
                beans.getBeanProvider(LoginLogMapper.class),
                beans.getBeanProvider(OperationLogMapper.class),
                beans.getBeanProvider(RiskRuleMapper.class),
                beans.getBeanProvider(SecBlacklistMapper.class),
                beans.getBeanProvider(AuditQueryMapper.class),
                beans.getBeanProvider(AuditTrailService.class));

        assertThatThrownBy(service::listHighRiskCatalog).isInstanceOf(IllegalStateException.class);
        var query = mock(AuditQueryMapper.class);
        beans.addBean("auditQueryMapper", query);
        when(query.selectHighRiskCatalog(1L)).thenReturn(List.of());
        try (var scope = TenantAccessContextHolder.openTenant(1L, "platform", 21L)) {
            assertThat(service.listHighRiskCatalog()).isEmpty();
        }
    }

    @Test
    void evaluatesRiskRuleUsingDbMetricsAndThreshold() {
        LoginLogMapper loginMapper = mock(LoginLogMapper.class);
        OperationLogMapper opMapper = mock(OperationLogMapper.class);
        RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
        SecBlacklistMapper blacklistMapper = mock(SecBlacklistMapper.class);

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

        AuditService service = new AuditService(
                bf.getBeanProvider(LoginLogMapper.class),
                bf.getBeanProvider(OperationLogMapper.class),
                bf.getBeanProvider(RiskRuleMapper.class),
                bf.getBeanProvider(SecBlacklistMapper.class),
                bf.getBeanProvider(AuditQueryMapper.class),
                bf.getBeanProvider(AuditTrailService.class)
        );

        RiskEvaluateResponse resp = service.evaluateRiskRule(new RiskEvaluateRequest("RULE_BRUTE_FORCE", "admin", "127.0.0.1", 1));
        assertThat(resp.decision()).isEqualTo("ALERT_AND_BLOCK");
        assertThat(resp.recentLoginFailures()).isEqualTo(6L);
    }
}
