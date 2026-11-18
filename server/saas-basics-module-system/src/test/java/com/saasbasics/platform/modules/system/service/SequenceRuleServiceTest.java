package com.saasbasics.platform.modules.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.SequenceNextResponse;
import com.saasbasics.platform.modules.system.entity.SequenceRuleEntity;
import com.saasbasics.platform.modules.system.mapper.SequenceRuleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class SequenceRuleServiceTest {

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void incrementsAndResetsSequencePersistently() {
        SequenceRuleMapper mapper = mock(SequenceRuleMapper.class);
        AuditTrailService auditTrailService = mock(AuditTrailService.class);
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("seqMapper", mapper);
        bf.addBean("auditTrail", auditTrailService);

        AuthContext.set(new AuthPrincipal(
                1L, 1L, "platform", 1L, "platform.admin", "Admin",
                "PLATFORM", "ENABLED", LocalDateTime.now().plusHours(2),
                List.of("*"), List.of(), List.of(1L), List.of()
        ));

        SequenceRuleEntity rule = new SequenceRuleEntity();
        rule.setId(10L);
        rule.setTenantId(1L);
        rule.setRuleCode("SEQ_ORDER");
        rule.setRuleName("商城与履约订单流水号");
        rule.setPrefix("ORD");
        rule.setDatePattern("yyyyMMdd");
        rule.setSeqLength(6);
        rule.setStep(1);
        rule.setCurrentValue(41L);
        rule.setResetCycle("DAILY");
        rule.setStatus("ENABLED");

        when(mapper.selectOne(any())).thenReturn(rule);
        when(mapper.updateById(any(SequenceRuleEntity.class))).thenReturn(1);

        SequenceRuleService service = new SequenceRuleService(
                bf.getBeanProvider(SequenceRuleMapper.class),
                bf.getBeanProvider(AuditTrailService.class)
        );

        SequenceNextResponse next = service.nextValue("SEQ_ORDER");
        assertThat(next.currentSeq()).isEqualTo(42L);
        assertThat(next.generatedValue()).startsWith("ORD_").endsWith("_000042");
        verify(mapper).updateById(rule);

        var reset = service.resetRule("SEQ_ORDER");
        assertThat(reset.currentSeq()).isEqualTo(0L);
        assertThat(reset.sample()).endsWith("_000000");
    }
}
