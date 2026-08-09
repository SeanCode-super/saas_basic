package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.RoleApiAssignmentRequest;
import com.saasbasics.platform.modules.iam.dto.RoleApiAssignmentResponse;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.dto.UserRoleAssignmentRequest;
import com.saasbasics.platform.modules.iam.dto.UserRoleAssignmentResponse;
import com.saasbasics.platform.modules.iam.entity.ApiResourceEntity;
import com.saasbasics.platform.modules.iam.entity.RoleApiEntity;
import com.saasbasics.platform.modules.iam.entity.UserRoleEntity;
import com.saasbasics.platform.modules.iam.mapper.ApiResourceMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleApiMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private static final Set<String> SYSTEM_ROLE_BASELINE_CODES = Set.of(
            "iam:user:query",
            "iam:role:query",
            "iam:api-resource:query",
            "iam:user-role:query",
            "iam:user-role:write",
            "iam:role-api:query",
            "iam:role-api:write",
            "system:config:query",
            "system:config:write"
    );

    private final ObjectProvider<UserRoleMapper> userRoleMapperProvider;
    private final ObjectProvider<RoleApiMapper> roleApiMapperProvider;
    private final ObjectProvider<RoleMapper> roleMapperProvider;
    private final ObjectProvider<ApiResourceMapper> apiResourceMapperProvider;
    private final AuditTrailService auditTrailService;

    public AuthorizationService(ObjectProvider<UserRoleMapper> userRoleMapperProvider,
                                ObjectProvider<RoleApiMapper> roleApiMapperProvider,
                                ObjectProvider<RoleMapper> roleMapperProvider,
                                ObjectProvider<ApiResourceMapper> apiResourceMapperProvider,
                                AuditTrailService auditTrailService) {
        this.userRoleMapperProvider = userRoleMapperProvider;
        this.roleApiMapperProvider = roleApiMapperProvider;
        this.roleMapperProvider = roleMapperProvider;
        this.apiResourceMapperProvider = apiResourceMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public UserRoleAssignmentResponse getUserRoleAssignments(Long userId, Long tenantId) {
        UserRoleMapper mapper = requiredUserRoleMapper();
        return new UserRoleAssignmentResponse(
                userId,
                tenantId,
                mapper.selectRoleIdsByUserId(tenantId, userId, LocalDateTime.now())
        );
    }

    public UserRoleAssignmentResponse assignUserRoles(Long userId, UserRoleAssignmentRequest request) {
        UserRoleMapper mapper = requiredUserRoleMapper();
        List<Long> roleIds = validateRoleIds(request.tenantId(), normalize(request.roleIds()));
        mapper.deleteAssignments(request.tenantId(), userId);

        for (Long roleId : roleIds) {
            UserRoleEntity entity = new UserRoleEntity();
            entity.setTenantId(request.tenantId());
            entity.setUserId(userId);
            entity.setRoleId(roleId);
            entity.setSourceType("MANUAL");
            mapper.insert(entity);
        }
        auditTrailService.record("iam", "user_role", String.valueOf(userId), "ASSIGN", "roles=" + request.roleIds(), "roles=" + roleIds, true);
        return new UserRoleAssignmentResponse(userId, request.tenantId(), roleIds);
    }

    public RoleApiAssignmentResponse getRoleApiAssignments(Long roleId, Long tenantId) {
        RoleApiMapper mapper = requiredRoleApiMapper();
        return new RoleApiAssignmentResponse(roleId, tenantId, mapper.selectApiResourceIdsByRoleId(tenantId, roleId));
    }

    public RoleApiAssignmentResponse assignRoleApis(Long roleId, RoleApiAssignmentRequest request) {
        RoleApiMapper mapper = requiredRoleApiMapper();
        List<Long> requestedIds = validateApiResourceIds(request.tenantId(), normalize(request.apiResourceIds()));
        List<Long> apiResourceIds = ensureRoleApiBaseline(roleId, request.tenantId(), requestedIds);

        mapper.deleteAssignments(request.tenantId(), roleId);

        for (Long apiResourceId : apiResourceIds) {
            RoleApiEntity entity = new RoleApiEntity();
            entity.setTenantId(request.tenantId());
            entity.setRoleId(roleId);
            entity.setApiResourceId(apiResourceId);
            mapper.insert(entity);
        }
        auditTrailService.record("iam", "role_api", String.valueOf(roleId), "ASSIGN", "apis=" + request.apiResourceIds(), "apis=" + apiResourceIds, true);
        return new RoleApiAssignmentResponse(roleId, request.tenantId(), apiResourceIds);
    }

    private List<Long> normalize(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(ids.stream().distinct().toList());
    }

    private UserRoleMapper requiredUserRoleMapper() {
        UserRoleMapper mapper = userRoleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "User role assignments require the db profile and MySQL connection");
        }
        return mapper;
    }

    private RoleApiMapper requiredRoleApiMapper() {
        RoleApiMapper mapper = roleApiMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Role api assignments require the db profile and MySQL connection");
        }
        return mapper;
    }

    private List<Long> ensureRoleApiBaseline(Long roleId, Long tenantId, List<Long> requestedIds) {
        RoleMapper roleMapper = requiredRoleMapper();
        ApiResourceMapper apiResourceMapper = requiredApiResourceMapper();

        RoleResponse role = roleMapper.selectRoleById(roleId);
        if (role == null) {
            throw new BizException("ROLE_NOT_FOUND", "Role is not available");
        }
        if (!Boolean.TRUE.equals(role.system())) {
            return requestedIds;
        }

        List<ApiResourceEntity> baselineResources = apiResourceMapper.selectList(new LambdaQueryWrapper<ApiResourceEntity>()
                .eq(ApiResourceEntity::getTenantId, tenantId)
                .in(ApiResourceEntity::getResourceCode, SYSTEM_ROLE_BASELINE_CODES)
                .eq(ApiResourceEntity::getStatus, "ENABLED"));

        LinkedHashSet<Long> mergedIds = new LinkedHashSet<>(requestedIds);
        for (ApiResourceEntity baselineResource : baselineResources) {
            mergedIds.add(baselineResource.getId());
        }
        return new ArrayList<>(mergedIds);
    }

    private List<Long> validateRoleIds(Long tenantId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return roleIds;
        }
        RoleMapper roleMapper = requiredRoleMapper();
        List<Long> validRoleIds = roleIds.stream()
                .filter(roleId -> {
                    RoleResponse role = roleMapper.selectRoleById(roleId);
                    return role != null && tenantId.equals(role.tenantId());
                })
                .toList();
        if (validRoleIds.size() != roleIds.size()) {
            throw new BizException("ROLE_NOT_FOUND", "One or more roles are not available for the current tenant");
        }
        return validRoleIds;
    }

    private List<Long> validateApiResourceIds(Long tenantId, List<Long> apiResourceIds) {
        if (apiResourceIds.isEmpty()) {
            return apiResourceIds;
        }
        ApiResourceMapper mapper = requiredApiResourceMapper();
        List<Long> validApiIds = mapper.selectList(new LambdaQueryWrapper<ApiResourceEntity>()
                        .eq(ApiResourceEntity::getTenantId, tenantId)
                        .in(ApiResourceEntity::getId, apiResourceIds)
                        .eq(ApiResourceEntity::getStatus, "ENABLED"))
                .stream()
                .map(ApiResourceEntity::getId)
                .toList();
        if (validApiIds.size() != apiResourceIds.size()) {
            throw new BizException("API_RESOURCE_NOT_FOUND", "One or more api resources are not available for the current tenant");
        }
        return validApiIds;
    }

    private RoleMapper requiredRoleMapper() {
        RoleMapper mapper = roleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Role resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }

    private ApiResourceMapper requiredApiResourceMapper() {
        ApiResourceMapper mapper = apiResourceMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Api resource resolution requires the db profile and MySQL connection");
        }
        return mapper;
    }
}
