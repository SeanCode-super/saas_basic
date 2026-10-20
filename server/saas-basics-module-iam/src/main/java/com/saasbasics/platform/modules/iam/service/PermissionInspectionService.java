package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.AccessSubjectProfile;
import com.saasbasics.platform.modules.iam.dto.DataPermissionRuleResponse;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.dto.PermissionSimulationRequest;
import com.saasbasics.platform.modules.iam.dto.PermissionSimulationResponse;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.dto.UserPermissionInspectionResponse;
import com.saasbasics.platform.modules.iam.dto.UserPermissionInspectionResponse.DataScopeSummary;
import com.saasbasics.platform.modules.iam.dto.UserPermissionInspectionResponse.MenuSummary;
import com.saasbasics.platform.modules.iam.dto.UserPermissionInspectionResponse.RoleSummary;
import com.saasbasics.platform.modules.iam.entity.DataPermissionRuleEntity;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.MenuPermissionEntity;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.DataPermissionRuleMapper;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuMapper;
import com.saasbasics.platform.modules.iam.mapper.MenuPermissionMapper;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PermissionInspectionService {

    private final AccessSubjectProfileService accessSubjectProfileService;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<PositionMapper> positionMapperProvider;
    private final ObjectProvider<RoleMapper> roleMapperProvider;
    private final ObjectProvider<MenuMapper> menuMapperProvider;
    private final ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider;
    private final ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider;
    private final ObjectMapper objectMapper;

    public PermissionInspectionService(AccessSubjectProfileService accessSubjectProfileService,
                                       ObjectProvider<UserMapper> userMapperProvider,
                                       ObjectProvider<EmployeeMapper> employeeMapperProvider,
                                       ObjectProvider<DepartmentMapper> departmentMapperProvider,
                                       ObjectProvider<PositionMapper> positionMapperProvider,
                                       ObjectProvider<RoleMapper> roleMapperProvider,
                                       ObjectProvider<MenuMapper> menuMapperProvider,
                                       ObjectProvider<MenuPermissionMapper> menuPermissionMapperProvider,
                                       ObjectProvider<DataPermissionRuleMapper> dataPermissionRuleMapperProvider,
                                       ObjectMapper objectMapper) {
        this.accessSubjectProfileService = accessSubjectProfileService;
        this.userMapperProvider = userMapperProvider;
        this.employeeMapperProvider = employeeMapperProvider;
        this.departmentMapperProvider = departmentMapperProvider;
        this.positionMapperProvider = positionMapperProvider;
        this.roleMapperProvider = roleMapperProvider;
        this.menuMapperProvider = menuMapperProvider;
        this.menuPermissionMapperProvider = menuPermissionMapperProvider;
        this.dataPermissionRuleMapperProvider = dataPermissionRuleMapperProvider;
        this.objectMapper = objectMapper;
    }

    public UserPermissionInspectionResponse inspect(Long tenantId, Long userId) {
        UserEntity user = requiredUserMapper().selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId())) {
            throw new BizException("USER_NOT_FOUND", "指定用户不存在或不属于当前租户");
        }

        AccessSubjectProfile profile = accessSubjectProfileService.load(tenantId, userId);

        String employeeName = "-";
        if (profile.employeeId() != null) {
            EmployeeEntity emp = requiredEmployeeMapper().selectById(profile.employeeId());
            if (emp != null) {
                employeeName = emp.getEmployeeName();
            }
        }

        String deptName = "-";
        if (profile.departmentId() != null) {
            DepartmentEntity dept = requiredDepartmentMapper().selectById(profile.departmentId());
            if (dept != null) {
                deptName = dept.getDeptFullName() != null ? dept.getDeptFullName() : dept.getDeptName();
            }
        }

        String positionName = "-";
        if (profile.positionId() != null) {
            PositionEntity pos = requiredPositionMapper().selectById(profile.positionId());
            if (pos != null) {
                positionName = pos.getPositionName();
            }
        }

        String companyDeptName = "-";
        if (profile.companyDepartmentId() != null) {
            DepartmentEntity comp = requiredDepartmentMapper().selectById(profile.companyDepartmentId());
            if (comp != null) {
                companyDeptName = comp.getDeptName();
            }
        }

        // 角色列表
        List<RoleSummary> roles = List.of();
        if (profile.roleIds() != null && !profile.roleIds().isEmpty()) {
            Set<Long> userRoleIds = new LinkedHashSet<>(profile.roleIds());
            roles = requiredRoleMapper().selectRoleList().stream()
                    .filter(r -> tenantId.equals(r.tenantId()) && userRoleIds.contains(r.id()))
                    .map(r -> new RoleSummary(r.id(), r.roleCode(), r.roleName(), r.roleType(), r.status()))
                    .toList();
        }

        // 菜单与按钮权限
        List<MenuPermissionEntity> grants = requiredMenuPermissionMapper().selectList(
                new LambdaQueryWrapper<MenuPermissionEntity>()
                        .eq(MenuPermissionEntity::getTenantId, tenantId)
                        .eq(MenuPermissionEntity::getDeleted, 0)
                        .eq(MenuPermissionEntity::getStatus, "ENABLED")
        );

        List<MenuPermissionEntity> matchedGrants = grants.stream()
                .filter(grant -> profile.matches(grant.getSubjectType(), grant.getSubjectValue()))
                .toList();

        Set<Long> grantedMenuIds = matchedGrants.stream()
                .map(MenuPermissionEntity::getMenuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<MenuResponse> allMenus = requiredMenuMapper().selectMenuList().stream()
                .filter(m -> tenantId.equals(m.tenantId()) && "ENABLED".equalsIgnoreCase(m.status()))
                .toList();
        Map<Long, MenuResponse> menuMap = allMenus.stream().collect(Collectors.toMap(MenuResponse::id, m -> m));

        LinkedHashSet<Long> visibleMenuIds = new LinkedHashSet<>();
        for (Long menuId : grantedMenuIds) {
            Long current = menuId;
            while (current != null && current > 0 && visibleMenuIds.add(current)) {
                MenuResponse parent = menuMap.get(current);
                if (parent == null || parent.parentId() == null || parent.parentId() <= 0) {
                    break;
                }
                current = parent.parentId();
            }
        }

        List<MenuSummary> menuItems = allMenus.stream()
                .filter(m -> visibleMenuIds.contains(m.id()))
                .map(m -> new MenuSummary(m.id(), m.parentId(), m.menuCode(), m.menuName(), m.menuType(), m.routePath(), m.icon()))
                .toList();

        LinkedHashSet<String> buttonPermissions = new LinkedHashSet<>();
        for (MenuPermissionEntity grant : matchedGrants) {
            buttonPermissions.addAll(parseButtonCodes(grant.getMenuCode(), grant.getButtonCodesJson()));
        }

        // 数据权限规则
        List<DataPermissionRuleResponse> allRules = requiredDataPermissionRuleMapper().selectRuleList().stream()
                .filter(r -> tenantId.equals(r.tenantId()) && "ENABLED".equalsIgnoreCase(r.status()))
                .toList();

        List<DataScopeSummary> dataScopeSummaries = new ArrayList<>();
        for (DataPermissionRuleResponse rule : allRules) {
            if (profile.matches(rule.subjectType(), rule.subjectValue())) {
                List<Long> resolvedDeptIds = resolveDepartmentIdsForRule(tenantId, profile, rule.scopeType(), rule.configJson());
                dataScopeSummaries.add(new DataScopeSummary(
                        rule.id(),
                        rule.resourceCode(),
                        rule.resourceName(),
                        rule.scopeType(),
                        rule.subjectType(),
                        rule.subjectValue(),
                        resolvedDeptIds,
                        rule.remark() != null ? rule.remark() : rule.scopeType()
                ));
            }
        }

        return new UserPermissionInspectionResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                profile.employeeId(),
                employeeName,
                profile.departmentId(),
                deptName,
                profile.positionId(),
                positionName,
                profile.companyDepartmentId(),
                companyDeptName,
                roles,
                menuItems,
                new ArrayList<>(buttonPermissions),
                dataScopeSummaries
        );
    }

    public PermissionSimulationResponse simulate(Long tenantId, PermissionSimulationRequest request) {
        UserPermissionInspectionResponse inspection = inspect(tenantId, request.userId());
        List<String> trace = new ArrayList<>();

        trace.add(String.format("1. [身份识别] 成功加载用户: %s (%s)，组织归属: %s，职位: %s",
                inspection.nickname(), inspection.username(), inspection.departmentName(), inspection.positionName()));

        List<String> roleNames = inspection.roles().stream()
                .map(RoleSummary::roleName)
                .toList();
        trace.add(String.format("2. [角色绑定] 用户当前持有 %d 个有效角色: [%s]",
                inspection.roles().size(), String.join(", ", roleNames)));

        // 功能操作校验
        boolean actionGranted = true;
        List<String> matchedRoles = new ArrayList<>();
        if (request.actionCode() != null && !request.actionCode().isBlank()) {
            String code = request.actionCode().trim();
            boolean found = inspection.buttonPermissions().contains(code) ||
                    inspection.buttonPermissions().stream().anyMatch(b -> b.endsWith(":" + code) || code.endsWith(":" + b));
            if (found) {
                actionGranted = true;
                matchedRoles.addAll(roleNames);
                trace.add(String.format("3. [功能校验] ✅ PASS: 操作代码 [%s] 命中有效授权，属于角色授权能力清单", code));
            } else {
                actionGranted = false;
                trace.add(String.format("3. [功能校验] ❌ DENY: 操作代码 [%s] 未在用户的角色授权列表内，操作将被权限拦截器拒绝", code));
            }
        } else if (request.menuCode() != null && !request.menuCode().isBlank()) {
            String menu = request.menuCode().trim();
            boolean found = inspection.menus().stream().anyMatch(m -> menu.equalsIgnoreCase(m.menuCode()));
            if (found) {
                actionGranted = true;
                trace.add(String.format("3. [页面校验] ✅ PASS: 页面 [%s] 在用户的可访问菜单目录内", menu));
            } else {
                actionGranted = false;
                trace.add(String.format("3. [页面校验] ❌ DENY: 页面 [%s] 未对当前用户开放", menu));
            }
        } else {
            trace.add("3. [功能校验] ℹ️ SKIP: 未指定具体操作或页面，跳过功能权限校验");
        }

        // 数据权限校验
        boolean dataScopeGranted = true;
        String resolvedScopeType = "ALL";
        List<Long> allowedDepartmentIds = new ArrayList<>();
        String simulatedSql = String.format("WHERE t.tenant_id = %d", tenantId);

        if (inspection.dataScopeRules().isEmpty()) {
            resolvedScopeType = "ALL";
            trace.add("4. [数据权限] ℹ️ 未配置显式行级拦截规则，系统应用平台默认租户级放行策略 (ALL)");
        } else {
            // 查看最高权限规则
            boolean hasAll = inspection.dataScopeRules().stream()
                    .anyMatch(r -> "ALL".equalsIgnoreCase(r.scopeType()) || "TENANT".equalsIgnoreCase(r.scopeType()));
            if (hasAll) {
                resolvedScopeType = "ALL";
                trace.add("4. [数据权限] ✅ PASS: 命中顶级数据规则 (ALL)，拥有租户全部业务数据的查阅权限");
            } else {
                LinkedHashSet<Long> allDeptIds = new LinkedHashSet<>();
                for (DataScopeSummary rule : inspection.dataScopeRules()) {
                    if (rule.resolvedDepartmentIds() != null) {
                        allDeptIds.addAll(rule.resolvedDepartmentIds());
                    }
                    resolvedScopeType = rule.scopeType();
                }
                allowedDepartmentIds = new ArrayList<>(allDeptIds);
                trace.add(String.format("4. [数据权限] 规则解析: 范围类型 [%s]，合并可访问部门 ID 列表: %s",
                        resolvedScopeType, allowedDepartmentIds));

                if (!allowedDepartmentIds.isEmpty()) {
                    String deptIn = allowedDepartmentIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
                    simulatedSql = String.format("WHERE t.tenant_id = %d AND t.dept_id IN (%s)", tenantId, deptIn);
                }

                if (request.targetDepartmentId() != null) {
                    if (allowedDepartmentIds.contains(request.targetDepartmentId())) {
                        dataScopeGranted = true;
                        trace.add(String.format("5. [目标验证] ✅ PASS: 目标访问部门 (#%d) 落在允许的部门授权范围内", request.targetDepartmentId()));
                    } else {
                        dataScopeGranted = false;
                        trace.add(String.format("5. [目标验证] ❌ DENY: 目标访问部门 (#%d) 超出允许的部门范围，SQL 层面将被过滤返回空集合", request.targetDepartmentId()));
                    }
                }
            }
        }

        boolean overallGranted = actionGranted && dataScopeGranted;
        trace.add(String.format("6. [最终裁定] %s: 综合评估结果为 %s",
                overallGranted ? "✅ 允许通行" : "❌ 访问拒绝",
                overallGranted ? "PASS" : "DENY"));

        return new PermissionSimulationResponse(
                overallGranted,
                actionGranted,
                dataScopeGranted,
                resolvedScopeType,
                allowedDepartmentIds,
                matchedRoles,
                trace,
                simulatedSql
        );
    }

    private List<Long> resolveDepartmentIdsForRule(Long tenantId, AccessSubjectProfile profile, String scopeType, String configJson) {
        if (scopeType == null) {
            return List.of();
        }
        String type = scopeType.toUpperCase(Locale.ROOT);
        switch (type) {
            case "ALL", "TENANT":
                return List.of();
            case "DEPT_AND_CHILDREN", "DEPARTMENT":
                if (profile.departmentId() != null) {
                    return accessSubjectProfileService.expandDepartmentTree(tenantId, List.of(profile.departmentId()));
                }
                return List.of();
            case "DEPT_ONLY":
                return profile.departmentId() != null ? List.of(profile.departmentId()) : List.of();
            case "ORG_AND_CHILDREN":
                if (profile.companyDepartmentId() != null) {
                    return accessSubjectProfileService.expandDepartmentTree(tenantId, List.of(profile.companyDepartmentId()));
                }
                return List.of();
            case "CUSTOM":
                if (configJson != null && !configJson.isBlank()) {
                    try {
                        Map<String, Object> map = objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
                        Object depts = map.get("departmentIds");
                        if (depts instanceof List<?> list) {
                            return list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList();
                        }
                    } catch (Exception ignored) {
                    }
                }
                return List.of();
            default:
                return List.of();
        }
    }

    private List<String> parseButtonCodes(String menuCode, String buttonCodesJson) {
        if (buttonCodesJson == null || buttonCodesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> codes = objectMapper.readValue(buttonCodesJson, new TypeReference<List<String>>() {});
            return codes.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .map(c -> c.contains(":") ? c : menuCode + ":" + c)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "UserMapper required");
        return mapper;
    }

    private EmployeeMapper requiredEmployeeMapper() {
        EmployeeMapper mapper = employeeMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "EmployeeMapper required");
        return mapper;
    }

    private DepartmentMapper requiredDepartmentMapper() {
        DepartmentMapper mapper = departmentMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "DepartmentMapper required");
        return mapper;
    }

    private PositionMapper requiredPositionMapper() {
        PositionMapper mapper = positionMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "PositionMapper required");
        return mapper;
    }

    private RoleMapper requiredRoleMapper() {
        RoleMapper mapper = roleMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "RoleMapper required");
        return mapper;
    }

    private MenuMapper requiredMenuMapper() {
        MenuMapper mapper = menuMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "MenuMapper required");
        return mapper;
    }

    private MenuPermissionMapper requiredMenuPermissionMapper() {
        MenuPermissionMapper mapper = menuPermissionMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "MenuPermissionMapper required");
        return mapper;
    }

    private DataPermissionRuleMapper requiredDataPermissionRuleMapper() {
        DataPermissionRuleMapper mapper = dataPermissionRuleMapperProvider.getIfAvailable();
        if (mapper == null) throw new BizException("DB_PROFILE_REQUIRED", "DataPermissionRuleMapper required");
        return mapper;
    }
}
