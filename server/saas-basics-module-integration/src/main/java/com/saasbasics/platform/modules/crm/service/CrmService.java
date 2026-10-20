package com.saasbasics.platform.modules.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.crm.dto.*;
import com.saasbasics.platform.modules.crm.entity.*;
import com.saasbasics.platform.modules.crm.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CrmService {

    private final CrmClueMapper clueMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmBusinessMapper businessMapper;
    private final CrmContractMapper contractMapper;
    private final CrmReceivableMapper receivableMapper;
    private final CrmFollowUpRecordMapper followUpRecordMapper;

    public CrmService(
            CrmClueMapper clueMapper,
            CrmCustomerMapper customerMapper,
            CrmBusinessMapper businessMapper,
            CrmContractMapper contractMapper,
            CrmReceivableMapper receivableMapper,
            CrmFollowUpRecordMapper followUpRecordMapper
    ) {
        this.clueMapper = clueMapper;
        this.customerMapper = customerMapper;
        this.businessMapper = businessMapper;
        this.contractMapper = contractMapper;
        this.receivableMapper = receivableMapper;
        this.followUpRecordMapper = followUpRecordMapper;
    }

    private Long getCurrentTenantId() {
        AuthPrincipal principal = AuthContext.get();
        return (principal != null && principal.tenantId() != null) ? principal.tenantId() : 1L;
    }

    private Long getCurrentUserId() {
        AuthPrincipal principal = AuthContext.get();
        return (principal != null && principal.userId() != null) ? principal.userId() : 1L;
    }

    private String getCurrentUserName() {
        AuthPrincipal principal = AuthContext.get();
        return (principal != null && principal.username() != null) ? principal.username() : "超级管理员";
    }

    // ==================== 1. 核心看板与数据统计 ====================
    public CrmStatisticsResponse getStatistics() {
        Long tenantId = getCurrentTenantId();

        Long totalCustomers = customerMapper.selectCount(new LambdaQueryWrapper<CrmCustomerEntity>()
                .eq(CrmCustomerEntity::getTenantId, tenantId)
                .eq(CrmCustomerEntity::getDeleted, 0));

        Long poolCustomers = customerMapper.selectCount(new LambdaQueryWrapper<CrmCustomerEntity>()
                .eq(CrmCustomerEntity::getTenantId, tenantId)
                .eq(CrmCustomerEntity::getIsPool, 1)
                .eq(CrmCustomerEntity::getDeleted, 0));

        Long totalClues = clueMapper.selectCount(new LambdaQueryWrapper<CrmClueEntity>()
                .eq(CrmClueEntity::getTenantId, tenantId)
                .ne(CrmClueEntity::getStatus, "CONVERTED")
                .eq(CrmClueEntity::getDeleted, 0));

        List<CrmBusinessEntity> businesses = businessMapper.selectList(new LambdaQueryWrapper<CrmBusinessEntity>()
                .eq(CrmBusinessEntity::getTenantId, tenantId)
                .eq(CrmBusinessEntity::getDeleted, 0));
        BigDecimal totalBusinessAmount = businesses.stream()
                .map(b -> b.getTotalPrice() != null ? b.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CrmContractEntity> contracts = contractMapper.selectList(new LambdaQueryWrapper<CrmContractEntity>()
                .eq(CrmContractEntity::getTenantId, tenantId)
                .eq(CrmContractEntity::getStatus, "APPROVED")
                .eq(CrmContractEntity::getDeleted, 0));
        BigDecimal contractWonAmount = contracts.stream()
                .map(c -> c.getTotalPrice() != null ? c.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CrmReceivableEntity> receivables = receivableMapper.selectList(new LambdaQueryWrapper<CrmReceivableEntity>()
                .eq(CrmReceivableEntity::getTenantId, tenantId)
                .eq(CrmReceivableEntity::getDeleted, 0));
        BigDecimal receivableReceived = receivables.stream()
                .filter(r -> "APPROVED".equalsIgnoreCase(r.getStatus()))
                .map(r -> r.getPrice() != null ? r.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivablePending = receivables.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .map(r -> r.getPrice() != null ? r.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CrmStatisticsResponse(
                totalCustomers,
                poolCustomers,
                totalClues,
                totalBusinessAmount,
                contractWonAmount,
                receivableReceived,
                receivablePending
        );
    }

    // ==================== 2. 线索管理 (Clue) ====================
    public List<CrmClueResponse> listClues(String status, String keyword) {
        Long tenantId = getCurrentTenantId();
        LambdaQueryWrapper<CrmClueEntity> wrapper = new LambdaQueryWrapper<CrmClueEntity>()
                .eq(CrmClueEntity::getTenantId, tenantId)
                .eq(CrmClueEntity::getDeleted, 0)
                .orderByDesc(CrmClueEntity::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(CrmClueEntity::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(CrmClueEntity::getName, keyword)
                    .or().like(CrmClueEntity::getContactName, keyword)
                    .or().like(CrmClueEntity::getMobile, keyword));
        }

        return clueMapper.selectList(wrapper).stream().map(c -> new CrmClueResponse(
                c.getId(), c.getName(), c.getContactName(), c.getMobile(), c.getTelephone(),
                c.getEmail(), c.getAddress(), c.getSource(), c.getStatus(), c.getOwnerUserId(),
                c.getOwnerUserName(), c.getNextContactTime(), c.getConvertedCustomerId(),
                c.getRemark(), c.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmClueResponse createClue(CrmClueSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmClueEntity entity = new CrmClueEntity();
        entity.setTenantId(tenantId);
        entity.setName(req.getName());
        entity.setContactName(req.getContactName());
        entity.setMobile(req.getMobile());
        entity.setTelephone(req.getTelephone());
        entity.setEmail(req.getEmail());
        entity.setAddress(req.getAddress());
        entity.setSource(req.getSource() != null ? req.getSource() : "ONLINE");
        entity.setStatus(req.getStatus() != null ? req.getStatus() : "UNFOLLOWED");
        entity.setOwnerUserId(getCurrentUserId());
        entity.setOwnerUserName(getCurrentUserName());
        entity.setNextContactTime(req.getNextContactTime());
        entity.setRemark(req.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        clueMapper.insert(entity);

        return new CrmClueResponse(
                entity.getId(), entity.getName(), entity.getContactName(), entity.getMobile(),
                entity.getTelephone(), entity.getEmail(), entity.getAddress(), entity.getSource(),
                entity.getStatus(), entity.getOwnerUserId(), entity.getOwnerUserName(),
                entity.getNextContactTime(), null, entity.getRemark(), entity.getCreatedAt()
        );
    }

    @Transactional
    public CrmCustomerResponse convertClueToCustomer(Long clueId) {
        Long tenantId = getCurrentTenantId();
        CrmClueEntity clue = clueMapper.selectById(clueId);
        if (clue == null || clue.getDeleted() == 1 || !tenantId.equals(clue.getTenantId())) {
            throw new BizException("CLUE_NOT_FOUND", "指定的销售线索不存在");
        }
        if ("CONVERTED".equalsIgnoreCase(clue.getStatus())) {
            throw new BizException("CLUE_ALREADY_CONVERTED", "该线索已被转化为客户，无需重复转化");
        }

        CrmCustomerEntity customer = new CrmCustomerEntity();
        customer.setTenantId(tenantId);
        customer.setCustomerCode("CUST-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-" + (1000 + ThreadLocalRandom.current().nextInt(9000)));
        customer.setName(clue.getName());
        customer.setIndustry("DEFAULT");
        customer.setLevel("B");
        customer.setSource(clue.getSource());
        customer.setMobile(clue.getMobile());
        customer.setTelephone(clue.getTelephone());
        customer.setEmail(clue.getEmail());
        customer.setAddress(clue.getAddress());
        customer.setDealStatus(0);
        customer.setIsPool(0);
        customer.setLockStatus(0);
        customer.setOwnerUserId(getCurrentUserId());
        customer.setOwnerUserName(getCurrentUserName());
        customer.setLastContactTime(LocalDateTime.now());
        customer.setNextContactTime(clue.getNextContactTime());
        customer.setRemark("由销售线索 [" + clue.getName() + "] 转化生成");
        customer.setCreatedAt(LocalDateTime.now());
        customer.setCreatedBy(getCurrentUserId());
        customer.setUpdatedAt(LocalDateTime.now());
        customer.setUpdatedBy(getCurrentUserId());
        customer.setDeleted(0);

        customerMapper.insert(customer);

        clue.setStatus("CONVERTED");
        clue.setConvertedCustomerId(customer.getId());
        clue.setUpdatedAt(LocalDateTime.now());
        clueMapper.updateById(clue);

        CrmFollowUpRecordEntity record = new CrmFollowUpRecordEntity();
        record.setTenantId(tenantId);
        record.setBizType("CUSTOMER");
        record.setBizId(customer.getId());
        record.setFollowType("OTHER");
        record.setContent("线索一键转化为企业正式客户，由销售人员 [" + getCurrentUserName() + "] 承接推进");
        record.setContactUserName(getCurrentUserName());
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(getCurrentUserId());
        record.setUpdatedAt(LocalDateTime.now());
        record.setUpdatedBy(getCurrentUserId());
        record.setDeleted(0);
        followUpRecordMapper.insert(record);

        return new CrmCustomerResponse(
                customer.getId(), customer.getCustomerCode(), customer.getName(), customer.getIndustry(),
                customer.getLevel(), customer.getSource(), customer.getMobile(), customer.getTelephone(),
                customer.getEmail(), customer.getAddress(), customer.getDealStatus(), customer.getIsPool(),
                customer.getLockStatus(), customer.getOwnerUserId(), customer.getOwnerUserName(),
                customer.getLastContactTime(), customer.getNextContactTime(), customer.getRemark(),
                customer.getCreatedAt()
        );
    }

    // ==================== 3. 客户与公海池管理 (Customer & Pool) ====================
    public List<CrmCustomerResponse> listCustomers(Integer isPool, String keyword, String level) {
        Long tenantId = getCurrentTenantId();
        LambdaQueryWrapper<CrmCustomerEntity> wrapper = new LambdaQueryWrapper<CrmCustomerEntity>()
                .eq(CrmCustomerEntity::getTenantId, tenantId)
                .eq(CrmCustomerEntity::getDeleted, 0)
                .orderByDesc(CrmCustomerEntity::getId);

        if (isPool != null) {
            wrapper.eq(CrmCustomerEntity::getIsPool, isPool);
        }
        if (level != null && !level.isBlank()) {
            wrapper.eq(CrmCustomerEntity::getLevel, level);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(CrmCustomerEntity::getName, keyword)
                    .or().like(CrmCustomerEntity::getCustomerCode, keyword)
                    .or().like(CrmCustomerEntity::getMobile, keyword));
        }

        return customerMapper.selectList(wrapper).stream().map(c -> new CrmCustomerResponse(
                c.getId(), c.getCustomerCode(), c.getName(), c.getIndustry(), c.getLevel(),
                c.getSource(), c.getMobile(), c.getTelephone(), c.getEmail(), c.getAddress(),
                c.getDealStatus(), c.getIsPool(), c.getLockStatus(), c.getOwnerUserId(),
                c.getOwnerUserName(), c.getLastContactTime(), c.getNextContactTime(),
                c.getRemark(), c.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmCustomerResponse createCustomer(CrmCustomerSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity entity = new CrmCustomerEntity();
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-" + (1000 + ThreadLocalRandom.current().nextInt(9000)));
        entity.setName(req.getName());
        entity.setIndustry(req.getIndustry() != null ? req.getIndustry() : "INTERNET");
        entity.setLevel(req.getLevel() != null ? req.getLevel() : "B");
        entity.setSource(req.getSource() != null ? req.getSource() : "ONLINE");
        entity.setMobile(req.getMobile());
        entity.setTelephone(req.getTelephone());
        entity.setEmail(req.getEmail());
        entity.setAddress(req.getAddress());
        entity.setDealStatus(req.getDealStatus() != null ? req.getDealStatus() : 0);
        entity.setIsPool(0);
        entity.setLockStatus(req.getLockStatus() != null ? req.getLockStatus() : 0);
        entity.setOwnerUserId(getCurrentUserId());
        entity.setOwnerUserName(getCurrentUserName());
        entity.setLastContactTime(LocalDateTime.now());
        entity.setNextContactTime(req.getNextContactTime());
        entity.setRemark(req.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        customerMapper.insert(entity);

        return new CrmCustomerResponse(
                entity.getId(), entity.getCustomerCode(), entity.getName(), entity.getIndustry(),
                entity.getLevel(), entity.getSource(), entity.getMobile(), entity.getTelephone(),
                entity.getEmail(), entity.getAddress(), entity.getDealStatus(), entity.getIsPool(),
                entity.getLockStatus(), entity.getOwnerUserId(), entity.getOwnerUserName(),
                entity.getLastContactTime(), entity.getNextContactTime(), entity.getRemark(),
                entity.getCreatedAt()
        );
    }

    @Transactional
    public void claimCustomer(Long customerId) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getDeleted() == 1 || !tenantId.equals(customer.getTenantId())) {
            throw new BizException("CUSTOMER_NOT_FOUND", "指定的客户档案不存在");
        }
        if (customer.getIsPool() == 0) {
            throw new BizException("ALREADY_CLAIMED", "该客户已被其他销售人员领取");
        }

        customer.setIsPool(0);
        customer.setOwnerUserId(getCurrentUserId());
        customer.setOwnerUserName(getCurrentUserName());
        customer.setLastContactTime(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);

        addInternalFollowUp("CUSTOMER", customerId, "销售员 [" + getCurrentUserName() + "] 从公海池成功抢单认领该客户");
    }

    @Transactional
    public void returnCustomerToPool(Long customerId) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getDeleted() == 1 || !tenantId.equals(customer.getTenantId())) {
            throw new BizException("CUSTOMER_NOT_FOUND", "指定的客户档案不存在");
        }
        if (customer.getLockStatus() == 1) {
            throw new BizException("CUSTOMER_LOCKED", "该客户处于锁定保护状态，严禁退回公海");
        }

        customer.setIsPool(1);
        customer.setOwnerUserId(null);
        customer.setOwnerUserName("公海池");
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);

        addInternalFollowUp("CUSTOMER", customerId, "销售人员退回客户至公海池，开放全体销售认领");
    }

    public void toggleCustomerLock(Long customerId) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getDeleted() == 1 || !tenantId.equals(customer.getTenantId())) {
            throw new BizException("CUSTOMER_NOT_FOUND", "指定的客户档案不存在");
        }

        customer.setLockStatus(customer.getLockStatus() == 1 ? 0 : 1);
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);
    }

    // ==================== 4. 商机漏斗 (Business Opportunity) ====================
    public List<CrmBusinessResponse> listBusinesses(String stage, String keyword) {
        Long tenantId = getCurrentTenantId();
        LambdaQueryWrapper<CrmBusinessEntity> wrapper = new LambdaQueryWrapper<CrmBusinessEntity>()
                .eq(CrmBusinessEntity::getTenantId, tenantId)
                .eq(CrmBusinessEntity::getDeleted, 0)
                .orderByDesc(CrmBusinessEntity::getId);

        if (stage != null && !stage.isBlank()) {
            wrapper.eq(CrmBusinessEntity::getStage, stage);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(CrmBusinessEntity::getName, keyword)
                    .or().like(CrmBusinessEntity::getCustomerName, keyword)
                    .or().like(CrmBusinessEntity::getBusinessCode, keyword));
        }

        return businessMapper.selectList(wrapper).stream().map(b -> new CrmBusinessResponse(
                b.getId(), b.getBusinessCode(), b.getName(), b.getCustomerId(), b.getCustomerName(),
                b.getTotalPrice(), b.getDealDate(), b.getStage(), b.getWinRate(), b.getLossReason(),
                b.getOwnerUserId(), b.getOwnerUserName(), b.getRemark(), b.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmBusinessResponse createBusiness(CrmBusinessSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity customer = customerMapper.selectById(req.getCustomerId());
        String customerName = customer != null ? customer.getName() : "未命名客户";

        CrmBusinessEntity entity = new CrmBusinessEntity();
        entity.setTenantId(tenantId);
        entity.setBusinessCode("BIZ-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-" + (1000 + ThreadLocalRandom.current().nextInt(9000)));
        entity.setName(req.getName());
        entity.setCustomerId(req.getCustomerId());
        entity.setCustomerName(customerName);
        entity.setTotalPrice(req.getTotalPrice() != null ? req.getTotalPrice() : BigDecimal.ZERO);
        entity.setDealDate(req.getDealDate());
        entity.setStage(req.getStage() != null ? req.getStage() : "INITIAL");
        entity.setWinRate(req.getWinRate() != null ? req.getWinRate() : 20);
        entity.setLossReason(req.getLossReason());
        entity.setOwnerUserId(getCurrentUserId());
        entity.setOwnerUserName(getCurrentUserName());
        entity.setRemark(req.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        businessMapper.insert(entity);

        return new CrmBusinessResponse(
                entity.getId(), entity.getBusinessCode(), entity.getName(), entity.getCustomerId(),
                entity.getCustomerName(), entity.getTotalPrice(), entity.getDealDate(), entity.getStage(),
                entity.getWinRate(), entity.getLossReason(), entity.getOwnerUserId(), entity.getOwnerUserName(),
                entity.getRemark(), entity.getCreatedAt()
        );
    }

    @Transactional
    public void updateBusinessStage(Long businessId, String stage, Integer winRate, String lossReason) {
        Long tenantId = getCurrentTenantId();
        CrmBusinessEntity business = businessMapper.selectById(businessId);
        if (business == null || business.getDeleted() == 1 || !tenantId.equals(business.getTenantId())) {
            throw new BizException("BUSINESS_NOT_FOUND", "指定的销售商机不存在");
        }

        business.setStage(stage);
        if (winRate != null) {
            business.setWinRate(winRate);
        }
        if (lossReason != null) {
            business.setLossReason(lossReason);
        }
        business.setUpdatedAt(LocalDateTime.now());
        businessMapper.updateById(business);

        if ("WON".equalsIgnoreCase(stage)) {
            CrmCustomerEntity customer = customerMapper.selectById(business.getCustomerId());
            if (customer != null) {
                customer.setDealStatus(1);
                customer.setUpdatedAt(LocalDateTime.now());
                customerMapper.updateById(customer);
            }
        }

        addInternalFollowUp("BUSINESS", businessId, "商机推进至阶段: [" + stage + "], 预测赢率: " + business.getWinRate() + "%");
    }

    // ==================== 5. 商务合同 (Contract) ====================
    public List<CrmContractResponse> listContracts(String status, String keyword) {
        Long tenantId = getCurrentTenantId();
        LambdaQueryWrapper<CrmContractEntity> wrapper = new LambdaQueryWrapper<CrmContractEntity>()
                .eq(CrmContractEntity::getTenantId, tenantId)
                .eq(CrmContractEntity::getDeleted, 0)
                .orderByDesc(CrmContractEntity::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(CrmContractEntity::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(CrmContractEntity::getName, keyword)
                    .or().like(CrmContractEntity::getContractNo, keyword)
                    .or().like(CrmContractEntity::getCustomerName, keyword));
        }

        return contractMapper.selectList(wrapper).stream().map(c -> new CrmContractResponse(
                c.getId(), c.getContractNo(), c.getName(), c.getCustomerId(), c.getCustomerName(),
                c.getBusinessId(), c.getBusinessName(), c.getTotalPrice(), c.getStartTime(),
                c.getEndTime(), c.getSignerUserName(), c.getStatus(), c.getRemark(), c.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmContractResponse createContract(CrmContractSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmCustomerEntity customer = customerMapper.selectById(req.getCustomerId());
        String customerName = customer != null ? customer.getName() : "客户档案";

        String businessName = "";
        if (req.getBusinessId() != null) {
            CrmBusinessEntity biz = businessMapper.selectById(req.getBusinessId());
            if (biz != null) businessName = biz.getName();
        }

        CrmContractEntity entity = new CrmContractEntity();
        entity.setTenantId(tenantId);
        entity.setContractNo("CT-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-" + (1000 + ThreadLocalRandom.current().nextInt(9000)));
        entity.setName(req.getName());
        entity.setCustomerId(req.getCustomerId());
        entity.setCustomerName(customerName);
        entity.setBusinessId(req.getBusinessId());
        entity.setBusinessName(businessName);
        entity.setTotalPrice(req.getTotalPrice() != null ? req.getTotalPrice() : BigDecimal.ZERO);
        entity.setStartTime(req.getStartTime());
        entity.setEndTime(req.getEndTime());
        entity.setSignerUserName(req.getSignerUserName() != null ? req.getSignerUserName() : getCurrentUserName());
        entity.setStatus("APPROVING");
        entity.setRemark(req.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        contractMapper.insert(entity);

        return new CrmContractResponse(
                entity.getId(), entity.getContractNo(), entity.getName(), entity.getCustomerId(),
                entity.getCustomerName(), entity.getBusinessId(), entity.getBusinessName(),
                entity.getTotalPrice(), entity.getStartTime(), entity.getEndTime(),
                entity.getSignerUserName(), entity.getStatus(), entity.getRemark(), entity.getCreatedAt()
        );
    }

    @Transactional
    public void approveContract(Long contractId, boolean approve) {
        Long tenantId = getCurrentTenantId();
        CrmContractEntity contract = contractMapper.selectById(contractId);
        if (contract == null || contract.getDeleted() == 1 || !tenantId.equals(contract.getTenantId())) {
            throw new BizException("CONTRACT_NOT_FOUND", "指定的商务合同不存在");
        }

        contract.setStatus(approve ? "APPROVED" : "REJECTED");
        contract.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(contract);

        addInternalFollowUp("CONTRACT", contractId, "合同审批节点裁决: [" + (approve ? "审批通过并生效" : "驳回并终止") + "], 审批人: " + getCurrentUserName());
    }

    // ==================== 6. 财务回款 (Receivable) ====================
    public List<CrmReceivableResponse> listReceivables(String status) {
        Long tenantId = getCurrentTenantId();
        LambdaQueryWrapper<CrmReceivableEntity> wrapper = new LambdaQueryWrapper<CrmReceivableEntity>()
                .eq(CrmReceivableEntity::getTenantId, tenantId)
                .eq(CrmReceivableEntity::getDeleted, 0)
                .orderByDesc(CrmReceivableEntity::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(CrmReceivableEntity::getStatus, status);
        }

        return receivableMapper.selectList(wrapper).stream().map(r -> new CrmReceivableResponse(
                r.getId(), r.getReceivableNo(), r.getContractId(), r.getContractName(),
                r.getCustomerId(), r.getCustomerName(), r.getPrice(), r.getReturnTime(),
                r.getPayType(), r.getStatus(), r.getRemark(), r.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmReceivableResponse createReceivable(CrmReceivableSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmContractEntity contract = contractMapper.selectById(req.getContractId());
        if (contract == null) {
            throw new BizException("CONTRACT_NOT_FOUND", "关联合同不存在");
        }

        CrmReceivableEntity entity = new CrmReceivableEntity();
        entity.setTenantId(tenantId);
        entity.setReceivableNo("REC-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "-" + (10 + ThreadLocalRandom.current().nextInt(90)));
        entity.setContractId(contract.getId());
        entity.setContractName(contract.getName());
        entity.setCustomerId(contract.getCustomerId());
        entity.setCustomerName(contract.getCustomerName());
        entity.setPrice(req.getPrice());
        entity.setReturnTime(req.getReturnTime());
        entity.setPayType(req.getPayType() != null ? req.getPayType() : "BANK_TRANSFER");
        entity.setStatus("PENDING");
        entity.setRemark(req.getRemark());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        receivableMapper.insert(entity);

        return new CrmReceivableResponse(
                entity.getId(), entity.getReceivableNo(), entity.getContractId(), entity.getContractName(),
                entity.getCustomerId(), entity.getCustomerName(), entity.getPrice(), entity.getReturnTime(),
                entity.getPayType(), entity.getStatus(), entity.getRemark(), entity.getCreatedAt()
        );
    }

    public void approveReceivable(Long receivableId) {
        Long tenantId = getCurrentTenantId();
        CrmReceivableEntity entity = receivableMapper.selectById(receivableId);
        if (entity == null || entity.getDeleted() == 1 || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("RECEIVABLE_NOT_FOUND", "指定的回款单据不存在");
        }

        entity.setStatus("APPROVED");
        entity.setUpdatedAt(LocalDateTime.now());
        receivableMapper.updateById(entity);
    }

    // ==================== 7. 全链路跟进记录 (Follow-Up Records) ====================
    public List<CrmFollowUpRecordResponse> listFollowUpRecords(String bizType, Long bizId) {
        Long tenantId = getCurrentTenantId();
        return followUpRecordMapper.selectList(new LambdaQueryWrapper<CrmFollowUpRecordEntity>()
                .eq(CrmFollowUpRecordEntity::getTenantId, tenantId)
                .eq(CrmFollowUpRecordEntity::getBizType, bizType)
                .eq(CrmFollowUpRecordEntity::getBizId, bizId)
                .eq(CrmFollowUpRecordEntity::getDeleted, 0)
                .orderByDesc(CrmFollowUpRecordEntity::getId)).stream().map(r -> new CrmFollowUpRecordResponse(
                r.getId(), r.getBizType(), r.getBizId(), r.getFollowType(), r.getContent(),
                r.getNextContactTime(), r.getContactUserName(), r.getCreatedAt()
        )).collect(Collectors.toList());
    }

    public CrmFollowUpRecordResponse addFollowUpRecord(CrmFollowUpRecordSaveRequest req) {
        Long tenantId = getCurrentTenantId();
        CrmFollowUpRecordEntity entity = new CrmFollowUpRecordEntity();
        entity.setTenantId(tenantId);
        entity.setBizType(req.getBizType());
        entity.setBizId(req.getBizId());
        entity.setFollowType(req.getFollowType() != null ? req.getFollowType() : "CALL");
        entity.setContent(req.getContent());
        entity.setNextContactTime(req.getNextContactTime());
        entity.setContactUserName(getCurrentUserName());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);

        followUpRecordMapper.insert(entity);

        if ("CUSTOMER".equalsIgnoreCase(req.getBizType())) {
            CrmCustomerEntity customer = customerMapper.selectById(req.getBizId());
            if (customer != null) {
                customer.setLastContactTime(LocalDateTime.now());
                if (req.getNextContactTime() != null) {
                    customer.setNextContactTime(req.getNextContactTime());
                }
                customer.setUpdatedAt(LocalDateTime.now());
                customerMapper.updateById(customer);
            }
        }

        return new CrmFollowUpRecordResponse(
                entity.getId(), entity.getBizType(), entity.getBizId(), entity.getFollowType(),
                entity.getContent(), entity.getNextContactTime(), entity.getContactUserName(),
                entity.getCreatedAt()
        );
    }

    private void addInternalFollowUp(String bizType, Long bizId, String content) {
        CrmFollowUpRecordEntity entity = new CrmFollowUpRecordEntity();
        entity.setTenantId(getCurrentTenantId());
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setFollowType("OTHER");
        entity.setContent(content);
        entity.setContactUserName(getCurrentUserName());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(getCurrentUserId());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(getCurrentUserId());
        entity.setDeleted(0);
        followUpRecordMapper.insert(entity);
    }
}
