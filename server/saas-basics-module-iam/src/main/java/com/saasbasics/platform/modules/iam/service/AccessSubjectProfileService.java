package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.CurrentDepartmentResolver;
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
import com.saasbasics.platform.modules.iam.api.UserPersonDirectory;
import com.saasbasics.platform.modules.organization.api.OrganizationDirectory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccessSubjectProfileService implements CurrentDepartmentResolver {

    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<UserRoleMapper> userRoleMapperProvider;
    private final ObjectProvider<SystemCacheService> cacheServiceProvider;
    private final ObjectProvider<UserPersonDirectory> userPersonDirectoryProvider;
    private final ObjectProvider<OrganizationDirectory> organizationDirectoryProvider;
    private volatile boolean legacyEmployeeFallbackEnabled = true;

    public AccessSubjectProfileService(ObjectProvider<UserMapper> userMapperProvider,
                                       ObjectProvider<EmployeeMapper> employeeMapperProvider,
                                       ObjectProvider<DepartmentMapper> departmentMapperProvider,
                                       ObjectProvider<UserRoleMapper> userRoleMapperProvider,
                                       ObjectProvider<SystemCacheService> cacheServiceProvider) {
        this(userMapperProvider, employeeMapperProvider, departmentMapperProvider, userRoleMapperProvider,
                cacheServiceProvider, null, null);
    }

    @Autowired
    public AccessSubjectProfileService(ObjectProvider<UserMapper> userMapperProvider,
                                       ObjectProvider<EmployeeMapper> employeeMapperProvider,
                                       ObjectProvider<DepartmentMapper> departmentMapperProvider,
                                       ObjectProvider<UserRoleMapper> userRoleMapperProvider,
                                       ObjectProvider<SystemCacheService> cacheServiceProvider,
                                       ObjectProvider<UserPersonDirectory> userPersonDirectoryProvider,
                                       ObjectProvider<OrganizationDirectory> organizationDirectoryProvider) {
        this.userMapperProvider = userMapperProvider;
        this.employeeMapperProvider = employeeMapperProvider;
        this.departmentMapperProvider = departmentMapperProvider;
        this.userRoleMapperProvider = userRoleMapperProvider;
        this.cacheServiceProvider = cacheServiceProvider;
        this.userPersonDirectoryProvider = userPersonDirectoryProvider;
        this.organizationDirectoryProvider = organizationDirectoryProvider;
    }

    @Value("${saas-basics.iam.legacy-employee-fallback-enabled:true}")
    public void setLegacyEmployeeFallbackEnabled(boolean legacyEmployeeFallbackEnabled) {
        this.legacyEmployeeFallbackEnabled = legacyEmployeeFallbackEnabled;
    }

    public boolean isLegacyEmployeeFallbackEnabled() {
        return legacyEmployeeFallbackEnabled;
    }

    public AccessSubjectProfile load(Long tenantId, Long userId) {
        UserEntity user = requiredUserMapper().selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BizException("AUTH_SUBJECT_NOT_FOUND", "当前登录主体不存在");
        }

        UUID personPublicId = null;
        UUID organizationPublicId = null;
        UUID orgUnitPublicId = null;
        UUID assignmentPublicId = null;
        UserPersonDirectory userPersonDir = userPersonDirectoryProvider != null
                ? userPersonDirectoryProvider.getIfAvailable() : null;
        OrganizationDirectory orgDir = organizationDirectoryProvider != null
                ? organizationDirectoryProvider.getIfAvailable() : null;
        if (userPersonDir != null && user.getPublicId() != null && !user.getPublicId().isBlank()) {
            try {
                var binding = userPersonDir.findEffectiveByUser(UUID.fromString(user.getPublicId()), Instant.now());
                if (binding.isPresent()) {
                    personPublicId = binding.get().personPublicId();
                    if (orgDir != null) {
                        var assignments = orgDir.findEffectiveAssignments(personPublicId, Instant.now());
                        if (!assignments.isEmpty()) {
                            var primary = assignments.get(0);
                            assignmentPublicId = primary.publicId();
                            organizationPublicId = primary.organizationPublicId();
                            orgUnitPublicId = primary.orgUnitPublicId();
                        }
                    }
                }
            } catch (Exception ignored) {
                // Standard binding unavailable in current context; fall through when legacy fallback enabled
            }
        }

        Long employeeId = null;
        Long departmentId = null;
        Long positionId = null;
        Long companyDepartmentId = null;
        boolean usedLegacyFallback = false;
        if (legacyEmployeeFallbackEnabled) {
            employeeId = normalizeId(user.getEmployeeId());
            if (employeeId != null) {
                EmployeeEntity employee = requiredEmployeeMapper().selectById(employeeId);
                if (employee != null && tenantId.equals(employee.getTenantId())) {
                    departmentId = normalizeId(employee.getDeptId());
                    positionId = normalizeId(employee.getPositionId());
                    companyDepartmentId = resolveCompanyDepartmentId(departmentId);
                    usedLegacyFallback = (personPublicId == null);
                }
            }
        }

        List<Long> roleIds = requiredUserRoleMapper().selectRoleIdsByUserId(tenantId, userId, LocalDateTime.now());
        return new AccessSubjectProfile(tenantId, userId, employeeId, departmentId, positionId,
                companyDepartmentId, roleIds, personPublicId, organizationPublicId, orgUnitPublicId,
                assignmentPublicId, usedLegacyFallback);
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

    @Override
    public Long resolveDepartmentId(Long tenantId, Long userId) {
        if (tenantId == null || tenantId <= 0L || userId == null || userId <= 0L) {
            return null;
        }
        try {
            UserEntity user = findActiveUser(tenantId, userId);
            if (user == null) {
                return null;
            }
            Long employeeId = normalizeId(user.getEmployeeId());
            if (employeeId == null) {
                return null;
            }
            EmployeeMapper employeeMapper = employeeMapperProvider.getIfAvailable();
            if (employeeMapper == null) {
                return null;
            }
            EmployeeEntity employee = employeeMapper.selectById(employeeId);
            if (employee == null || !tenantId.equals(employee.getTenantId()) || Integer.valueOf(1).equals(employee.getDeleted())) {
                return null;
            }
            return normalizeId(employee.getDeptId());
        } catch (Exception ex) {
            return null;
        }
    }

    public UserEntity findActiveUser(Long tenantId, Long userId) {
        UserMapper userMapper = userMapperProvider.getIfAvailable();
        if (userMapper == null || tenantId == null || userId == null) {
            return null;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId()) || Integer.valueOf(1).equals(user.getDeleted())) {
            return null;
        }
        return user;
    }

    public List<Long> resolveUserIdsByDepartments(Long tenantId, Collection<Long> deptIds) {
        if (tenantId == null || deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        EmployeeMapper employeeMapper = employeeMapperProvider.getIfAvailable();
        UserMapper userMapper = userMapperProvider.getIfAvailable();
        if (employeeMapper == null || userMapper == null) {
            return List.of();
        }
        List<EmployeeEntity> employees = employeeMapper.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getTenantId, tenantId)
                .eq(EmployeeEntity::getDeleted, 0)
                .in(EmployeeEntity::getDeptId, deptIds));
        return mapEmployeesToUserIds(tenantId, userMapper, employees);
    }

    public List<Long> resolveUserIdsByPositions(Long tenantId, Collection<Long> positionIds) {
        if (tenantId == null || positionIds == null || positionIds.isEmpty()) {
            return List.of();
        }
        EmployeeMapper employeeMapper = employeeMapperProvider.getIfAvailable();
        UserMapper userMapper = userMapperProvider.getIfAvailable();
        if (employeeMapper == null || userMapper == null) {
            return List.of();
        }
        List<EmployeeEntity> employees = employeeMapper.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getTenantId, tenantId)
                .eq(EmployeeEntity::getDeleted, 0)
                .in(EmployeeEntity::getPositionId, positionIds));
        return mapEmployeesToUserIds(tenantId, userMapper, employees);
    }

    public List<Long> resolveUserIdsByRoles(Long tenantId, Collection<Long> roleIds) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        UserRoleMapper userRoleMapper = userRoleMapperProvider.getIfAvailable();
        if (userRoleMapper == null) {
            return List.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            if (roleId != null && roleId > 0) {
                List<Long> ids = userRoleMapper.selectUserIdsByRoleId(tenantId, roleId, now);
                if (ids != null) {
                    result.addAll(ids);
                }
            }
        }
        return List.copyOf(result);
    }

    public List<Long> resolveSubordinateUserIds(Long tenantId, Long leaderUserId) {
        if (leaderUserId == null || leaderUserId <= 0) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(leaderUserId);
        DepartmentMapper deptMapper = departmentMapperProvider.getIfAvailable();
        if (tenantId != null && deptMapper != null) {
            List<DepartmentEntity> managedDepts = deptMapper.selectList(new LambdaQueryWrapper<DepartmentEntity>()
                    .eq(DepartmentEntity::getTenantId, tenantId)
                    .eq(DepartmentEntity::getDeleted, 0)
                    .eq(DepartmentEntity::getLeaderUserId, leaderUserId));
            if (managedDepts != null && !managedDepts.isEmpty()) {
                List<Long> rootIds = managedDepts.stream().map(DepartmentEntity::getId).toList();
                List<Long> expandedDeptIds = expandDepartmentTree(tenantId, rootIds);
                userIds.addAll(resolveUserIdsByDepartments(tenantId, expandedDeptIds));
            }
        }
        return List.copyOf(userIds);
    }

    private List<Long> mapEmployeesToUserIds(Long tenantId, UserMapper userMapper, List<EmployeeEntity> employees) {
        if (employees == null || employees.isEmpty()) {
            return List.of();
        }
        List<Long> employeeIds = employees.stream()
                .map(EmployeeEntity::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getTenantId, tenantId)
                .eq(UserEntity::getDeleted, 0)
                .in(UserEntity::getEmployeeId, employeeIds));
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(UserEntity::getId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
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
