package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.dto.UserSaveRequest;
import com.saasbasics.platform.modules.iam.dto.UserStatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final ObjectProvider<UserMapper> userMapperProvider;
    private final PasswordSecurityService passwordSecurityService;
    private final DataPermissionRuleService dataPermissionRuleService;

    public UserService(ObjectProvider<UserMapper> userMapperProvider,
                       PasswordSecurityService passwordSecurityService,
                       DataPermissionRuleService dataPermissionRuleService) {
        this.userMapperProvider = userMapperProvider;
        this.passwordSecurityService = passwordSecurityService;
        this.dataPermissionRuleService = dataPermissionRuleService;
    }

    public List<UserResponse> listUsers() {
        DataPermissionSqlSpec spec = dataPermissionRuleService.resolveCurrentSpec();
        return requiredMapper().selectUserList(spec);
    }

    public UserResponse getUser(Long id) {
        UserMapper mapper = requiredMapper();
        UserResponse user = mapper.selectUserById(id);
        if (user == null) {
            throw new BizException("IAM_USER_NOT_FOUND", "IAM user not found");
        }
        return user;
    }

    public UserResponse createUser(UserSaveRequest request) {
        UserEntity entity = new UserEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getUser(entity.getId());
    }

    public UserResponse updateUser(Long id, UserSaveRequest request) {
        UserMapper mapper = requiredMapper();
        UserEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_USER_NOT_FOUND", "IAM user not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getUser(id);
    }

    public UserResponse updateStatus(Long id, UserStatusUpdateRequest request) {
        UserMapper mapper = requiredMapper();
        UserEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_USER_NOT_FOUND", "IAM user not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getUser(id);
    }

    private void apply(UserEntity entity, UserSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setUserCode(request.userCode());
        entity.setUsername(request.username());
        entity.setNickname(request.nickname());
        entity.setEmployeeId(request.employeeId());
        entity.setUserType(request.userType());
        entity.setStatus(request.status());
        entity.setMobile(request.mobile());
        entity.setEmail(request.email());
        String rawPassword = request.password();
        if (rawPassword != null && !rawPassword.isBlank()) {
            entity.setPasswordHash(passwordSecurityService.hash(rawPassword));
            entity.setPasswordChangedAt(LocalDateTime.now());
        } else if (entity.getId() == null) {
            entity.setPasswordHash(passwordSecurityService.hash("Admin@123456"));
            entity.setPasswordChangedAt(LocalDateTime.now());
        }
        if (request.needResetPassword() != null) {
            entity.setNeedResetPassword(request.needResetPassword());
        } else if (entity.getId() == null) {
            entity.setNeedResetPassword(true);
        }
        entity.setRemark(request.remark());
    }

    private UserMapper requiredMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
