package com.saasbasics.platform.modules.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.api.CustomerFollowUpDirectory;
import com.saasbasics.platform.modules.crm.entity.CrmContractEntity;
import com.saasbasics.platform.modules.crm.entity.CrmCustomerEntity;
import com.saasbasics.platform.modules.crm.entity.CrmFollowUpRecordEntity;
import com.saasbasics.platform.modules.crm.mapper.CrmContractMapper;
import com.saasbasics.platform.modules.crm.mapper.CrmCustomerMapper;
import com.saasbasics.platform.modules.crm.mapper.CrmFollowUpRecordMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerFollowUpDirectoryService implements CustomerFollowUpDirectory {
    private final CrmCustomerMapper customers;
    private final CrmContractMapper contracts;
    private final CrmFollowUpRecordMapper followUps;

    public CustomerFollowUpDirectoryService(CrmCustomerMapper customers, CrmContractMapper contracts,
                                            CrmFollowUpRecordMapper followUps) {
        this.customers = customers;
        this.contracts = contracts;
        this.followUps = followUps;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Summary> recent(long tenantId, long customerId) {
        var customer = customers.selectOne(new LambdaQueryWrapper<CrmCustomerEntity>()
                .select(CrmCustomerEntity::getName)
                .eq(CrmCustomerEntity::getTenantId, tenantId).eq(CrmCustomerEntity::getId, customerId)
                .eq(CrmCustomerEntity::getDeleted, 0));
        if (customer == null) return Optional.empty();
        int recentContracts = contracts.selectList(new LambdaQueryWrapper<CrmContractEntity>()
                .select(CrmContractEntity::getId).eq(CrmContractEntity::getTenantId, tenantId)
                .eq(CrmContractEntity::getCustomerId, customerId).eq(CrmContractEntity::getDeleted, 0)
                .orderByDesc(CrmContractEntity::getId).last("LIMIT 5")).size();
        int recentFollowUps = followUps.selectList(new LambdaQueryWrapper<CrmFollowUpRecordEntity>()
                .select(CrmFollowUpRecordEntity::getId).eq(CrmFollowUpRecordEntity::getTenantId, tenantId)
                .eq(CrmFollowUpRecordEntity::getBizId, customerId).eq(CrmFollowUpRecordEntity::getBizType, "CUSTOMER")
                .eq(CrmFollowUpRecordEntity::getDeleted, 0).orderByDesc(CrmFollowUpRecordEntity::getId)
                .last("LIMIT 5")).size();
        return Optional.of(new Summary(customer.getName(), recentContracts, recentFollowUps));
    }
}
