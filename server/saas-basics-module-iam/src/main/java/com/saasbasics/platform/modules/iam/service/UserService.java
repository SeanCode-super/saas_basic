package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.saasbasics.platform.common.tenant.TenantMembershipCapacity;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.common.id.UuidV7Generator;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.dto.UserSaveRequest;
import com.saasbasics.platform.modules.iam.dto.UserStatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.PasswordPolicyEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.PasswordPolicyMapper;
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
    private final TenantMembershipCapacity capacity;
    private final ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider;
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    public UserService(ObjectProvider<UserMapper> userMapperProvider,
                       PasswordSecurityService passwordSecurityService,
                       DataPermissionRuleService dataPermissionRuleService,
                       TenantMembershipCapacity capacity,
                       ObjectProvider<PasswordPolicyMapper> passwordPolicyMapperProvider) {
        this.userMapperProvider = userMapperProvider;
        this.passwordSecurityService = passwordSecurityService;
        this.dataPermissionRuleService = dataPermissionRuleService;
        this.capacity = capacity;
        this.passwordPolicyMapperProvider = passwordPolicyMapperProvider;
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse createUser(UserSaveRequest request) {
        Long tenantId = TenantAccessContextHolder.requiredTenantId();
        if (request.tenantId() != null && !tenantId.equals(request.tenantId())) {
            throw new BizException("TENANT_ACCESS_DENIED", "账号必须在目标租户的上下文中创建");
        }
        capacity.requireSeat(tenantId);
        UserEntity entity = new UserEntity();
        entity.setPublicId(uuidGenerator.generate().toString());
        entity.setTenantId(tenantId);
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
        if (request.tenantId() != null && !request.tenantId().equals(entity.getTenantId())) {
            throw new BizException("TENANT_ACCESS_DENIED", "账号的所属租户不能修改");
        }
        if ("PLATFORM".equalsIgnoreCase(request.userType())) {
            var actor = AuthContext.get();
            if (actor == null || !actor.isPlatformTenant() || !"PLATFORM".equalsIgnoreCase(actor.userType())
                    || !actor.tenantId().equals(entity.getTenantId())) {
                throw new BizException("TENANT_ACCESS_DENIED", "平台身份只能由平台操作员在平台租户内授予");
            }
        }
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
            PasswordPolicyMapper policyMapper = passwordPolicyMapperProvider != null ? passwordPolicyMapperProvider.getIfAvailable() : null;
            Long tid = entity.getTenantId();
            PasswordPolicyEntity policy = policyMapper != null ? policyMapper.selectActivePolicy(tid) : null;
            passwordSecurityService.validate(rawPassword, policy);
            entity.setPasswordHash(passwordSecurityService.hash(rawPassword));
            entity.setPasswordChangedAt(LocalDateTime.now());
        } else if (entity.getId() == null) {
            throw new BizException("IAM_INITIAL_PASSWORD_REQUIRED", "创建登录账号必须填写初始密码");
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
