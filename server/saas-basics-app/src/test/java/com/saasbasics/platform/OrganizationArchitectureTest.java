package com.saasbasics.platform;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.dto.DepartmentTreeNodeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeTransferRequest;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.service.DepartmentService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = SaasBasicsApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class OrganizationArchitectureTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.3.0")
            .withDatabaseName("saas_basics")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private IamService iamService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private DepartmentService departmentService;

    private TenantAccessContextHolder.Scope tenantScope;

    @BeforeEach
    void setUpAuth() {
        AuthContext.set(new AuthPrincipal(
                1L, 1L, "platform", 1L, "platform.admin", "Platform Administrator", "PLATFORM", "ONLINE",
                LocalDateTime.now().plusHours(8), List.of("iam:user:query", "iam:user:write"), List.of(),
                List.of(1L), List.of("iam_employee:transfer")
        ));
        tenantScope = TenantAccessContextHolder.openTenant(1L, "platform", 1L);
    }

    @AfterEach
    void clearAuth() {
        if (tenantScope != null) {
            tenantScope.close();
        }
        TenantAccessContextHolder.clear();
        AuthContext.clear();
    }

    @Test
    void loadsLegacyOrganizationViewsDuringMigrationCompatibilityWindow() {
        List<DepartmentResponse> departments = iamService.departments();
        List<PositionResponse> positions = iamService.positions();
        List<EmployeeResponse> employees = iamService.employees();
        List<DepartmentTreeNodeResponse> tree = iamService.departmentTree();

        Assertions.assertFalse(departments.isEmpty());
        Assertions.assertFalse(positions.isEmpty());
        Assertions.assertFalse(employees.isEmpty());
        Assertions.assertFalse(tree.isEmpty());
        Assertions.assertNotNull(departments.get(0).treeLevel());
        Assertions.assertNotNull(departments.get(0).employeeCount());
        Assertions.assertNotNull(departments.get(0).childCount());
        Assertions.assertEquals(
                departments.get(0).id(),
                departmentService.getDepartment(departments.get(0).id()).id()
        );
        Assertions.assertNotNull(positions.get(0).employeeCount());
        Assertions.assertNotNull(employees.get(0).deptFullName());
        Assertions.assertNotNull(employees.get(0).boundUserCount());
    }

    @Test
    void transfersAndRestoresLegacyEmployeeDuringCompatibilityWindow() {
        List<EmployeeResponse> employees = iamService.employees();
        List<DepartmentResponse> departments = iamService.departments();
        List<PositionResponse> positions = iamService.positions();
        Assumptions.assumeTrue(!employees.isEmpty() && departments.size() > 1 && positions.size() > 1);

        EmployeeResponse employee = employees.get(0);
        DepartmentResponse targetDepartment = departments.stream()
                .filter(item -> !item.id().equals(employee.deptId())).findFirst().orElse(null);
        PositionResponse targetPosition = positions.stream()
                .filter(item -> !item.id().equals(employee.positionId())).findFirst().orElse(null);
        Assumptions.assumeTrue(targetDepartment != null && targetPosition != null);

        EmployeeResponse transferred = employeeService.transferEmployee(
                employee.id(), new EmployeeTransferRequest(targetDepartment.id(), targetPosition.id(), "compatibility-test"));
        Assertions.assertEquals(targetDepartment.id(), transferred.deptId());
        Assertions.assertEquals(targetPosition.id(), transferred.positionId());

        EmployeeResponse restored = employeeService.transferEmployee(
                employee.id(), new EmployeeTransferRequest(employee.deptId(), employee.positionId(), "compatibility-restore"));
        Assertions.assertEquals(employee.deptId(), restored.deptId());
        Assertions.assertEquals(employee.positionId(), restored.positionId());
    }
}
