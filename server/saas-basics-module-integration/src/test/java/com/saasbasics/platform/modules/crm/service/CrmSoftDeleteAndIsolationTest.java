package com.saasbasics.platform.modules.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.crm.entity.*;
import com.saasbasics.platform.modules.crm.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class CrmSoftDeleteAndIsolationTest {

    private CrmClueMapper clueMapper;
    private CrmCustomerMapper customerMapper;
    private CrmBusinessMapper businessMapper;
    private CrmContractMapper contractMapper;
    private CrmReceivableMapper receivableMapper;
    private CrmFollowUpRecordMapper followUpRecordMapper;
    private CrmService crmService;

    @BeforeEach
    void setUp() {
        clueMapper = mock(CrmClueMapper.class);
        customerMapper = mock(CrmCustomerMapper.class);
        businessMapper = mock(CrmBusinessMapper.class);
        contractMapper = mock(CrmContractMapper.class);
        receivableMapper = mock(CrmReceivableMapper.class);
        followUpRecordMapper = mock(CrmFollowUpRecordMapper.class);

        crmService = new CrmService(
                clueMapper, customerMapper, businessMapper, contractMapper,
                receivableMapper, followUpRecordMapper
        );

        AuthContext.set(new AuthPrincipal(
                1L, 1L, "platform", 100L, "test_admin", "Admin",
                "PLATFORM", "ENABLED", java.time.LocalDateTime.now().plusHours(2),
                List.of("*"), List.of(), List.of(1L), List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void verifiesSoftDeleteBusinessAndEnforcesTenantBoundary() {
        CrmBusinessEntity business = new CrmBusinessEntity();
        business.setId(10L);
        business.setTenantId(1L);
        business.setName("智能电网数字中台");
        business.setDeleted(0);

        when(businessMapper.selectById(10L)).thenReturn(business);

        crmService.deleteBusiness(10L);

        assertThat(business.getDeleted()).isEqualTo(1);
        assertThat(business.getDeletedAt()).isNotNull();
        assertThat(business.getUpdatedBy()).isEqualTo(100L);
        verify(businessMapper).updateById(business);

        // 跨租户删除应拦截
        CrmBusinessEntity crossTenantBiz = new CrmBusinessEntity();
        crossTenantBiz.setId(20L);
        crossTenantBiz.setTenantId(999L);
        crossTenantBiz.setDeleted(0);
        when(businessMapper.selectById(20L)).thenReturn(crossTenantBiz);

        assertThatThrownBy(() -> crmService.deleteBusiness(20L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo("BUSINESS_NOT_FOUND");
    }

    @Test
    void verifiesSoftDeleteCustomerAndClue() {
        CrmCustomerEntity customer = new CrmCustomerEntity();
        customer.setId(5L);
        customer.setTenantId(1L);
        customer.setName("苏州汇能动力");
        customer.setDeleted(0);
        when(customerMapper.selectById(5L)).thenReturn(customer);

        crmService.deleteCustomer(5L);
        assertThat(customer.getDeleted()).isEqualTo(1);
        assertThat(customer.getDeletedAt()).isNotNull();
        verify(customerMapper).updateById(customer);

        CrmClueEntity clue = new CrmClueEntity();
        clue.setId(8L);
        clue.setTenantId(1L);
        clue.setName("储能中台商机线索");
        clue.setDeleted(0);
        when(clueMapper.selectById(8L)).thenReturn(clue);

        crmService.deleteClue(8L);
        assertThat(clue.getDeleted()).isEqualTo(1);
        assertThat(clue.getDeletedAt()).isNotNull();
        verify(clueMapper).updateById(clue);
    }
}
