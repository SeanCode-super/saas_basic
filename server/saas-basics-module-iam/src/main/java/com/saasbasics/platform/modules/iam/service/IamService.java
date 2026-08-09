package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.dto.DepartmentTreeNodeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.dto.IamOverviewResponse;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.entity.ApiResourceEntity;
import com.saasbasics.platform.modules.iam.entity.DataScopeEntity;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.ApiResourceMapper;
import com.saasbasics.platform.modules.iam.mapper.DataScopeMapper;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import com.saasbasics.platform.modules.iam.mapper.RoleMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class IamService {

    private final ObjectProvider<UserMapper> userMapperProvider;
    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<PositionMapper> positionMapperProvider;
    private final ObjectProvider<RoleMapper> roleMapperProvider;
    private final ObjectProvider<ApiResourceMapper> apiResourceMapperProvider;
    private final ObjectProvider<DataScopeMapper> dataScopeMapperProvider;
    private final DataPermissionRuleService dataPermissionRuleService;

    public IamService(
            ObjectProvider<UserMapper> userMapperProvider,
            ObjectProvider<EmployeeMapper> employeeMapperProvider,
            ObjectProvider<DepartmentMapper> departmentMapperProvider,
            ObjectProvider<PositionMapper> positionMapperProvider,
            ObjectProvider<RoleMapper> roleMapperProvider,
            ObjectProvider<ApiResourceMapper> apiResourceMapperProvider,
            ObjectProvider<DataScopeMapper> dataScopeMapperProvider,
            DataPermissionRuleService dataPermissionRuleService
    ) {
        this.userMapperProvider = userMapperProvider;
        this.employeeMapperProvider = employeeMapperProvider;
        this.departmentMapperProvider = departmentMapperProvider;
        this.positionMapperProvider = positionMapperProvider;
        this.roleMapperProvider = roleMapperProvider;
        this.apiResourceMapperProvider = apiResourceMapperProvider;
        this.dataScopeMapperProvider = dataScopeMapperProvider;
        this.dataPermissionRuleService = dataPermissionRuleService;
    }

    public IamOverviewResponse overview() {
        UserMapper userMapper = required(userMapperProvider, "用户目录需要数据库连接");
        EmployeeMapper employeeMapper = required(employeeMapperProvider, "员工目录需要数据库连接");
        DepartmentMapper departmentMapper = required(departmentMapperProvider, "部门目录需要数据库连接");
        PositionMapper positionMapper = required(positionMapperProvider, "岗位目录需要数据库连接");
        RoleMapper roleMapper = required(roleMapperProvider, "角色目录需要数据库连接");
        ApiResourceMapper apiResourceMapper = required(apiResourceMapperProvider, "权限资源目录需要数据库连接");
        DataScopeMapper dataScopeMapper = required(dataScopeMapperProvider, "数据权限目录需要数据库连接");

        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("accounts", count(userMapper, UserEntity::getDeleted));
        counters.put("employees", count(employeeMapper, EmployeeEntity::getDeleted));
        counters.put("departments", count(departmentMapper, DepartmentEntity::getDeleted));
        counters.put("positions", count(positionMapper, PositionEntity::getDeleted));
        counters.put("roles", count(roleMapper, RoleEntity::getDeleted));
        counters.put("apiResources", count(apiResourceMapper, ApiResourceEntity::getDeleted));
        counters.put("dataScopes", count(dataScopeMapper, DataScopeEntity::getDeleted));

        return new IamOverviewResponse(
                defaultLayers(),
                defaultCapabilities(),
                counters
        );
    }

    public List<DepartmentResponse> departments() {
        DataPermissionSqlSpec spec = dataPermissionRuleService.resolveCurrentSpec();
        return required(departmentMapperProvider, "部门目录需要数据库连接").selectDepartmentList(spec);
    }

    public List<DepartmentTreeNodeResponse> departmentTree() {
        List<DepartmentResponse> departments = departments();
        Map<Long, List<DepartmentResponse>> childrenByParent = departments.stream()
                .collect(Collectors.groupingBy(
                        department -> department.parentId() == null ? 0L : department.parentId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return buildDepartmentNodes(childrenByParent, 0L);
    }

    public List<PositionResponse> positions() {
        DataPermissionSqlSpec spec = dataPermissionRuleService.resolveCurrentSpec();
        return required(positionMapperProvider, "岗位目录需要数据库连接").selectPositionList(spec);
    }

    public List<EmployeeResponse> employees() {
        DataPermissionSqlSpec spec = dataPermissionRuleService.resolveCurrentSpec();
        return required(employeeMapperProvider, "员工目录需要数据库连接").selectEmployeeList(spec);
    }

    private List<String> defaultLayers() {
        return List.of("account", "identity", "employee", "department", "position", "role", "apiResource", "dataScope");
    }

    private List<String> defaultCapabilities() {
        return List.of(
                "multi-identity binding",
                "role composition",
                "api permission registry",
                "data scope editor",
                "organization master data",
                "session and password policy"
        );
    }

    private List<DepartmentTreeNodeResponse> buildDepartmentNodes(Map<Long, List<DepartmentResponse>> childrenByParent,
                                                                  Long parentId) {
        List<DepartmentResponse> children = childrenByParent.getOrDefault(parentId, List.of());
        List<DepartmentTreeNodeResponse> nodes = new ArrayList<>();
        for (DepartmentResponse child : children) {
            nodes.add(new DepartmentTreeNodeResponse(
                    child.id(),
                    child.parentId(),
                    child.deptCode(),
                    child.deptName(),
                    child.deptFullName(),
                    child.treeLevel(),
                    child.leaderName(),
                    child.childCount(),
                    child.employeeCount(),
                    child.status(),
                    buildDepartmentNodes(childrenByParent, child.id())
            ));
        }
        return nodes;
    }

    private <T> long count(BaseMapper<T> mapper, SFunction<T, ?> deletedColumn) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<T>().eq(deletedColumn, 0);
        Long value = mapper.selectCount(wrapper);
        return value == null ? 0L : value;
    }

    private <T> T required(ObjectProvider<T> provider, String message) {
        T bean = provider.getIfAvailable();
        if (bean == null) {
            throw new com.saasbasics.platform.common.exception.BizException("DB_PROFILE_REQUIRED", message);
        }
        return bean;
    }
}
