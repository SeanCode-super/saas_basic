package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequireDataPermission;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.iam.dto.DepartmentSaveRequest;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.dto.DepartmentTreeNodeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeSaveRequest;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeTransferRequest;
import com.saasbasics.platform.modules.iam.dto.IamOverviewResponse;
import com.saasbasics.platform.modules.iam.dto.PositionSaveRequest;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.service.DepartmentService;
import com.saasbasics.platform.modules.iam.service.EmployeeService;
import com.saasbasics.platform.modules.iam.service.IamService;
import com.saasbasics.platform.modules.iam.service.PositionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam")
public class IamController {

    private final IamService iamService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final EmployeeService employeeService;

    public IamController(IamService iamService,
                         DepartmentService departmentService,
                         PositionService positionService,
                         EmployeeService employeeService) {
        this.iamService = iamService;
        this.departmentService = departmentService;
        this.positionService = positionService;
        this.employeeService = employeeService;
    }

    @GetMapping("/overview")
    @RequirePermission("iam:overview:query")
    public ApiResponse<IamOverviewResponse> overview() {
        return ApiResponse.success(iamService.overview());
    }

    @GetMapping("/departments")
    @RequirePermission("iam:user:query")
    @RequireDataPermission("iam:department:list")
    public ApiResponse<List<DepartmentResponse>> departments() {
        return ApiResponse.success(iamService.departments());
    }

    @GetMapping("/departments/tree")
    @RequirePermission("iam:user:query")
    @RequireDataPermission("iam:department:list")
    public ApiResponse<List<DepartmentTreeNodeResponse>> departmentTree() {
        return ApiResponse.success(iamService.departmentTree());
    }

    @GetMapping("/departments/{id}")
    @RequirePermission("iam:user:query")
    public ApiResponse<DepartmentResponse> departmentDetail(@PathVariable Long id) {
        return ApiResponse.success(departmentService.getDepartment(id));
    }

    @PostMapping("/departments")
    @RequirePermission("iam:user:write")
    public ApiResponse<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResponse.success(departmentService.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    @RequirePermission("iam:user:write")
    public ApiResponse<DepartmentResponse> updateDepartment(@PathVariable Long id,
                                                            @Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResponse.success(departmentService.updateDepartment(id, request));
    }

    @PatchMapping("/departments/{id}/status")
    @RequirePermission("iam:user:write")
    public ApiResponse<DepartmentResponse> updateDepartmentStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(departmentService.updateStatus(id, request));
    }

    @GetMapping("/positions")
    @RequirePermission("iam:user:query")
    @RequireDataPermission("iam:position:list")
    public ApiResponse<List<PositionResponse>> positions() {
        return ApiResponse.success(iamService.positions());
    }

    @GetMapping("/positions/{id}")
    @RequirePermission("iam:user:query")
    public ApiResponse<PositionResponse> positionDetail(@PathVariable Long id) {
        return ApiResponse.success(positionService.getPosition(id));
    }

    @PostMapping("/positions")
    @RequirePermission("iam:user:write")
    public ApiResponse<PositionResponse> createPosition(@Valid @RequestBody PositionSaveRequest request) {
        return ApiResponse.success(positionService.createPosition(request));
    }

    @PutMapping("/positions/{id}")
    @RequirePermission("iam:user:write")
    public ApiResponse<PositionResponse> updatePosition(@PathVariable Long id,
                                                        @Valid @RequestBody PositionSaveRequest request) {
        return ApiResponse.success(positionService.updatePosition(id, request));
    }

    @PatchMapping("/positions/{id}/status")
    @RequirePermission("iam:user:write")
    public ApiResponse<PositionResponse> updatePositionStatus(@PathVariable Long id,
                                                              @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(positionService.updateStatus(id, request));
    }

    @GetMapping("/employees")
    @RequirePermission("iam:user:query")
    @RequireDataPermission("iam:employee:list")
    public ApiResponse<List<EmployeeResponse>> employees() {
        return ApiResponse.success(iamService.employees());
    }

    @GetMapping("/employees/{id}")
    @RequirePermission("iam:user:query")
    public ApiResponse<EmployeeResponse> employeeDetail(@PathVariable Long id) {
        return ApiResponse.success(employeeService.getEmployee(id));
    }

    @PostMapping("/employees")
    @RequirePermission("iam:user:write")
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeSaveRequest request) {
        return ApiResponse.success(employeeService.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    @RequirePermission("iam:user:write")
    public ApiResponse<EmployeeResponse> updateEmployee(@PathVariable Long id,
                                                        @Valid @RequestBody EmployeeSaveRequest request) {
        return ApiResponse.success(employeeService.updateEmployee(id, request));
    }

    @PostMapping("/employees/{id}/transfer")
    @RequirePermission("iam:user:write")
    public ApiResponse<EmployeeResponse> transferEmployee(@PathVariable Long id,
                                                          @Valid @RequestBody EmployeeTransferRequest request) {
        return ApiResponse.success(employeeService.transferEmployee(id, request));
    }

    @PatchMapping("/employees/{id}/status")
    @RequirePermission("iam:user:write")
    public ApiResponse<EmployeeResponse> updateEmployeeStatus(@PathVariable Long id,
                                                              @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.success(employeeService.updateStatus(id, request));
    }
}
