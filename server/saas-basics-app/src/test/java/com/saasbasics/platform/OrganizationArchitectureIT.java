package com.saasbasics.platform;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.dto.DepartmentTreeNodeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeTransferRequest;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.service.EmployeeService;
import com.saasbasics.platform.modules.iam.service.IamService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = SaasBasicsApplication.class)
@ActiveProfiles("db")
class OrganizationArchitectureIT {

    @Autowired
    private IamService iamService;

    @Autowired
    private EmployeeService employeeService;

    @BeforeEach
    void setUpAuth() {
        AuthContext.set(new AuthPrincipal(
                1L,
                1L,
                "platform",
                1L,
                "platform.admin",
                "平台管理员",
                "PLATFORM",
                "ONLINE",
                LocalDateTime.now().plusHours(8),
                List.of("iam:user:query", "iam:user:write"),
                List.of(),
                List.of(1L),
                List.of("iam_employee:transfer")
        ));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void shouldLoadDepartmentTreeAndOrgStatistics() {
        List<DepartmentResponse> departments = iamService.departments();
        List<PositionResponse> positions = iamService.positions();
        List<EmployeeResponse> employees = iamService.employees();
        List<DepartmentTreeNodeResponse> tree = iamService.departmentTree();

        Assertions.assertFalse(departments.isEmpty(), "部门列表不能为空");
        Assertions.assertFalse(positions.isEmpty(), "岗位列表不能为空");
        Assertions.assertFalse(employees.isEmpty(), "员工列表不能为空");
        Assertions.assertFalse(tree.isEmpty(), "部门树不能为空");

        DepartmentResponse firstDepartment = departments.get(0);
        Assertions.assertNotNull(firstDepartment.treeLevel(), "部门层级不能为空");
        Assertions.assertNotNull(firstDepartment.employeeCount(), "部门在编人数不能为空");
        Assertions.assertNotNull(firstDepartment.childCount(), "部门下级数量不能为空");

        PositionResponse firstPosition = positions.get(0);
        Assertions.assertNotNull(firstPosition.employeeCount(), "岗位在岗人数不能为空");

        EmployeeResponse firstEmployee = employees.get(0);
        Assertions.assertNotNull(firstEmployee.deptFullName(), "员工部门全称不能为空");
        Assertions.assertNotNull(firstEmployee.boundUserCount(), "员工绑定账号数量不能为空");
    }

    @Test
    void shouldTransferEmployeeAndRestoreOrganizationBinding() {
        List<EmployeeResponse> employees = iamService.employees();
        List<DepartmentResponse> departments = iamService.departments();
        List<PositionResponse> positions = iamService.positions();

        Assumptions.assumeTrue(!employees.isEmpty() && departments.size() > 1 && positions.size() > 1,
                "当前种子数据不足以验证组织异动");

        EmployeeResponse employee = employees.get(0);
        DepartmentResponse targetDepartment = departments.stream()
                .filter(item -> !item.id().equals(employee.deptId()))
                .findFirst()
                .orElse(null);
        PositionResponse targetPosition = positions.stream()
                .filter(item -> !item.id().equals(employee.positionId()))
                .findFirst()
                .orElse(null);

        Assumptions.assumeTrue(targetDepartment != null && targetPosition != null, "没有可用的异动目标");

        EmployeeResponse transferred = employeeService.transferEmployee(
                employee.id(),
                new EmployeeTransferRequest(targetDepartment.id(), targetPosition.id(), "organization-it-transfer")
        );
        Assertions.assertEquals(targetDepartment.id(), transferred.deptId(), "异动后部门应变更");
        Assertions.assertEquals(targetPosition.id(), transferred.positionId(), "异动后岗位应变更");

        EmployeeResponse restored = employeeService.transferEmployee(
                employee.id(),
                new EmployeeTransferRequest(employee.deptId(), employee.positionId(), "organization-it-restore")
        );
        Assertions.assertEquals(employee.deptId(), restored.deptId(), "恢复后部门应还原");
        Assertions.assertEquals(employee.positionId(), restored.positionId(), "恢复后岗位应还原");
    }
}
