package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import com.saasbasics.platform.modules.iam.mapper.UserRoleMapper;
import com.saasbasics.platform.cache.SystemCacheService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AccessSubjectProfileService {

    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<UserRoleMapper> userRoleMapperProvider;
    private final ObjectProvider<SystemCacheService> cacheServiceProvider;

    public AccessSubjectProfileService(ObjectProvider<UserMapper> userMapperProvider,
                                       ObjectProvider<EmployeeMapper> employeeMapperProvider,
                                       ObjectProvider<DepartmentMapper> departmentMapperProvider,
                                       ObjectProvider<UserRoleMapper> userRoleMapperProvider,
                                       ObjectProvider<SystemCacheService> cacheServiceProvider) {
        this.userMapperProvider = userMapperProvider;
        this.employeeMapperProvider = employeeMapperProvider;
        this.departmentMapperProvider = departmentMapperProvider;
        this.userRoleMapperProvider = userRoleMapperProvider;
        this.cacheServiceProvider = cacheServiceProvider;
    }

    public AccessSubjectProfile load(Long tenantId, Long userId) {
        UserEntity user = requiredUserMapper().selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BizException("AUTH_SUBJECT_NOT_FOUND", "当前登录主体不存在");
        }

        Long employeeId = normalizeId(user.getEmployeeId());
        Long departmentId = null;
        Long positionId = null;
        Long companyDepartmentId = null;
        if (employeeId != null) {
            EmployeeEntity employee = requiredEmployeeMapper().selectById(employeeId);
            if (employee != null && tenantId.equals(employee.getTenantId())) {
                departmentId = normalizeId(employee.getDeptId());
                positionId = normalizeId(employee.getPositionId());
                companyDepartmentId = resolveCompanyDepartmentId(departmentId);
            }
        }

        List<Long> roleIds = requiredUserRoleMapper().selectRoleIdsByUserId(tenantId, userId, LocalDateTime.now());
        return new AccessSubjectProfile(tenantId, userId, employeeId, departmentId, positionId, companyDepartmentId, roleIds);
    }

    private Long resolveCompanyDepartmentId(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        DepartmentMapper mapper = requiredDepartmentMapper();
        DepartmentEntity current = mapper.selectById(departmentId);
        if (current == null) {
            return null;
        }
        while (current.getParentId() != null && current.getParentId() > 0) {
            DepartmentEntity parent = mapper.selectById(current.getParentId());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current.getId();
    }

    public List<Long> expandDepartmentTree(Long tenantId, List<Long> rootIds) {
        if (rootIds == null || rootIds.isEmpty()) {
            return List.of();
        }
        SystemCacheService cache = cacheServiceProvider.getIfAvailable();
        if (cache != null && rootIds.size() == 1) {
            String cacheKey = String.format(SystemCacheService.KEY_DEPT_TREE, tenantId, rootIds.get(0));
            return cache.getOrCompute(cacheKey, 7200, () -> computeDepartmentTree(tenantId, rootIds));
        }
        return computeDepartmentTree(tenantId, rootIds);
    }

    private List<Long> computeDepartmentTree(Long tenantId, List<Long> rootIds) {
        List<DepartmentEntity> departments = requiredDepartmentMapper().selectList(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getTenantId, tenantId)
                .eq(DepartmentEntity::getDeleted, 0));
        java.util.Map<Long, java.util.List<Long>> children = new java.util.HashMap<>();
        for (DepartmentEntity department : departments) {
            children.computeIfAbsent(department.getParentId() == null ? 0L : department.getParentId(), key -> new java.util.ArrayList<>())
                    .add(department.getId());
        }

        java.util.LinkedHashSet<Long> expanded = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>(rootIds);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (current == null || !expanded.add(current)) {
                continue;
            }
            for (Long childId : children.getOrDefault(current, List.of())) {
                queue.addLast(childId);
            }
        }
        return new java.util.ArrayList<>(expanded);
    }

    private Long normalizeId(Long id) {
        return id == null || id <= 0 ? null : id;
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "权限主体解析需要数据库连接");
        }
        return mapper;
    }

    private EmployeeMapper requiredEmployeeMapper() {
        EmployeeMapper mapper = employeeMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "权限主体解析需要数据库连接");
        }
        return mapper;
    }

    private DepartmentMapper requiredDepartmentMapper() {
        DepartmentMapper mapper = departmentMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "权限主体解析需要数据库连接");
        }
        return mapper;
    }

    private UserRoleMapper requiredUserRoleMapper() {
        UserRoleMapper mapper = userRoleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "权限主体解析需要数据库连接");
        }
        return mapper;
    }
}
