package com.saasbasics.platform.modules.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.audit.dto.AuditOverviewResponse;
import com.saasbasics.platform.modules.audit.dto.LoginLogResponse;
import com.saasbasics.platform.modules.audit.dto.OperationLogResponse;
import com.saasbasics.platform.modules.audit.entity.LoginLogEntity;
import com.saasbasics.platform.modules.audit.entity.OperationLogEntity;
import com.saasbasics.platform.modules.audit.entity.RiskRuleEntity;
import java.util.List;
import com.saasbasics.platform.modules.audit.mapper.LoginLogMapper;
import com.saasbasics.platform.modules.audit.mapper.OperationLogMapper;
import com.saasbasics.platform.modules.audit.mapper.RiskRuleMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final ObjectProvider<LoginLogMapper> loginLogMapperProvider;
    private final ObjectProvider<OperationLogMapper> operationLogMapperProvider;
    private final ObjectProvider<RiskRuleMapper> riskRuleMapperProvider;

    public AuditService(ObjectProvider<LoginLogMapper> loginLogMapperProvider,
                        ObjectProvider<OperationLogMapper> operationLogMapperProvider,
                        ObjectProvider<RiskRuleMapper> riskRuleMapperProvider) {
        this.loginLogMapperProvider = loginLogMapperProvider;
        this.operationLogMapperProvider = operationLogMapperProvider;
        this.riskRuleMapperProvider = riskRuleMapperProvider;
    }

    public AuditOverviewResponse overview() {
        LoginLogMapper loginLogMapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        OperationLogMapper operationLogMapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        RiskRuleMapper riskRuleMapper = required(riskRuleMapperProvider, "风险规则需要数据库连接");

        long loginCount = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLogEntity>());
        long operationCount = operationLogMapper.selectCount(new LambdaQueryWrapper<OperationLogEntity>());
        long riskRuleCount = riskRuleMapper.selectCount(new LambdaQueryWrapper<RiskRuleEntity>().eq(RiskRuleEntity::getDeleted, 0));
        return new AuditOverviewResponse(loginCount, operationCount, 0L, riskRuleCount);
    }

    public List<LoginLogResponse> listLoginLogs(String username, Boolean success) {
        LoginLogMapper mapper = required(loginLogMapperProvider, "登录审计需要数据库连接");
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .orderByDesc(LoginLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (username != null && !username.isBlank()) {
            wrapper.like(LoginLogEntity::getUsername, username.trim());
        }
        if (success != null) {
            wrapper.eq(LoginLogEntity::getSuccess, success);
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new LoginLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getUserId(),
                        item.getUsername(),
                        item.getLoginType(),
                        item.getLoginIp(),
                        item.getSuccess(),
                        item.getFailReason(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    public List<OperationLogResponse> listOperationLogs(String bizModule, String operationType) {
        OperationLogMapper mapper = required(operationLogMapperProvider, "操作审计需要数据库连接");
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .orderByDesc(OperationLogEntity::getOccurredAt)
                .last("LIMIT 100");
        if (bizModule != null && !bizModule.isBlank()) {
            wrapper.eq(OperationLogEntity::getBizModule, bizModule.trim());
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLogEntity::getOperationType, operationType.trim());
        }
        return mapper.selectList(wrapper)
                .stream()
                .map(item -> new OperationLogResponse(
                        item.getId(),
                        item.getTenantId(),
                        item.getOperatorUserId(),
                        item.getOperatorName(),
                        item.getBizModule(),
                        item.getBizType(),
                        item.getBizId(),
                        item.getOperationType(),
                        item.getSuccess(),
                        item.getOccurredAt()
                ))
                .toList();
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new IllegalStateException(message);
        }
        return bean;
    }
}
