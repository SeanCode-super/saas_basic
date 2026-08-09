package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicyResponse;
import com.saasbasics.platform.modules.iam.dto.PasswordPolicySaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PasswordPolicyService {

    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;

    public PasswordPolicyService(ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider) {
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
    }

    public List<PasswordPolicyResponse> listPolicies() {
        PasswordPolicyMapper mapper = passwordPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectPasswordPolicyList();
    }

    public PasswordPolicyResponse getPolicy(Long id) {
        PasswordPolicyResponse response = requiredMapper().selectPasswordPolicyById(id);
        if (response == null) {
            throw new BizException("IAM_PASSWORD_POLICY_NOT_FOUND", "IAM password policy not found");
        }
        return response;
    }

    public PasswordPolicyResponse createPolicy(PasswordPolicySaveRequest request) {
        PasswordPolicyEntity entity = new PasswordPolicyEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getPolicy(entity.getId());
    }

    public PasswordPolicyResponse updatePolicy(Long id, PasswordPolicySaveRequest request) {
        PasswordPolicyMapper mapper = requiredMapper();
        PasswordPolicyEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_PASSWORD_POLICY_NOT_FOUND", "IAM password policy not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getPolicy(id);
    }

    public PasswordPolicyResponse updateStatus(Long id, StatusUpdateRequest request) {
        PasswordPolicyMapper mapper = requiredMapper();
        PasswordPolicyEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_PASSWORD_POLICY_NOT_FOUND", "IAM password policy not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getPolicy(id);
    }

    private void apply(PasswordPolicyEntity entity, PasswordPolicySaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setPolicyCode(request.policyCode());
        entity.setPolicyName(request.policyName());
        entity.setMinLength(request.minLength());
        entity.setMaxLength(request.maxLength());
        entity.setRequireUppercase(request.requireUppercase());
        entity.setRequireLowercase(request.requireLowercase());
        entity.setRequireNumber(request.requireNumber());
        entity.setRequireSpecial(request.requireSpecial());
        entity.setPasswordHistoryLimit(request.passwordHistoryLimit());
        entity.setPasswordExpireDays(request.passwordExpireDays());
        entity.setTempPasswordExpireHours(request.tempPasswordExpireHours());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private PasswordPolicyMapper requiredMapper() {
        PasswordPolicyMapper mapper = passwordPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Password policy write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
