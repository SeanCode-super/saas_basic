package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.LoginPolicyResponse;
import com.saasbasics.platform.modules.iam.dto.LoginPolicySaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.LoginPolicyEntity;
import com.saasbasics.platform.modules.iam.mapper.LoginPolicyMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class LoginPolicyService {

    private final ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider;

    public LoginPolicyService(ObjectProvider<LoginPolicyMapper> loginPolicyMapperProvider) {
        this.loginPolicyMapperProvider = loginPolicyMapperProvider;
    }

    public List<LoginPolicyResponse> listPolicies() {
        LoginPolicyMapper mapper = loginPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectLoginPolicyList();
    }

    public LoginPolicyResponse getPolicy(Long id) {
        LoginPolicyResponse response = requiredMapper().selectLoginPolicyById(id);
        if (response == null) {
            throw new BizException("IAM_LOGIN_POLICY_NOT_FOUND", "IAM login policy not found");
        }
        return response;
    }

    public LoginPolicyResponse createPolicy(LoginPolicySaveRequest request) {
        LoginPolicyEntity entity = new LoginPolicyEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getPolicy(entity.getId());
    }

    public LoginPolicyResponse updatePolicy(Long id, LoginPolicySaveRequest request) {
        LoginPolicyMapper mapper = requiredMapper();
        LoginPolicyEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_LOGIN_POLICY_NOT_FOUND", "IAM login policy not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getPolicy(id);
    }

    public LoginPolicyResponse updateStatus(Long id, StatusUpdateRequest request) {
        LoginPolicyMapper mapper = requiredMapper();
        LoginPolicyEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_LOGIN_POLICY_NOT_FOUND", "IAM login policy not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getPolicy(id);
    }

    private void apply(LoginPolicyEntity entity, LoginPolicySaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setPolicyCode(request.policyCode());
        entity.setPolicyName(request.policyName());
        entity.setAllowPasswordLogin(request.allowPasswordLogin());
        entity.setAllowSmsLogin(request.allowSmsLogin());
        entity.setAllowEmailLogin(request.allowEmailLogin());
        entity.setAllowSocialLogin(request.allowSocialLogin());
        entity.setForceMfa(request.forceMfa());
        entity.setSessionTimeoutMinutes(request.sessionTimeoutMinutes());
        entity.setMaxFailedCount(request.maxFailedCount());
        entity.setLockMinutes(request.lockMinutes());
        entity.setIpAllowlistJson(request.ipAllowlistJson());
        entity.setDeviceTrustDays(request.deviceTrustDays());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private LoginPolicyMapper requiredMapper() {
        LoginPolicyMapper mapper = loginPolicyMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Login policy write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
