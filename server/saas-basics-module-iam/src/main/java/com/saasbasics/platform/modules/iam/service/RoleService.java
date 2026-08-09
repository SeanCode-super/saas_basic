package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.dto.RoleSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RoleService {

    private final ObjectProvider<RoleMapper> roleMapperProvider;

    public RoleService(ObjectProvider<RoleMapper> roleMapperProvider) {
        this.roleMapperProvider = roleMapperProvider;
    }

    public List<RoleResponse> listRoles() {
        return requiredMapper().selectRoleList();
    }

    public RoleResponse getRole(Long id) {
        RoleResponse role = requiredMapper().selectRoleById(id);
        if (role == null) {
            throw new BizException("IAM_ROLE_NOT_FOUND", "IAM role not found");
        }
        return role;
    }

    public RoleResponse createRole(RoleSaveRequest request) {
        RoleEntity entity = new RoleEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getRole(entity.getId());
    }

    public RoleResponse updateRole(Long id, RoleSaveRequest request) {
        RoleMapper mapper = requiredMapper();
        RoleEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_ROLE_NOT_FOUND", "IAM role not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getRole(id);
    }

    public RoleResponse updateStatus(Long id, StatusUpdateRequest request) {
        RoleMapper mapper = requiredMapper();
        RoleEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("IAM_ROLE_NOT_FOUND", "IAM role not found");
        }
        entity.setStatus(request.status());
        mapper.updateById(entity);
        return getRole(id);
    }

    private void apply(RoleEntity entity, RoleSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setRoleGroupId(request.roleGroupId() == null ? 0L : request.roleGroupId());
        entity.setRoleCode(request.roleCode());
        entity.setRoleName(request.roleName());
        entity.setRoleType(request.roleType());
        entity.setDataScopeType(request.dataScopeType());
        entity.setStatus(request.status());
        entity.setSystem(Boolean.TRUE.equals(request.system()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
    }

    private RoleMapper requiredMapper() {
        RoleMapper mapper = roleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Role write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
