package com.saasbasics.platform.modules.iam.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.dto.EmployeeSaveRequest;
import com.saasbasics.platform.modules.iam.dto.EmployeeTransferRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.EmployeeMapper;
import com.saasbasics.platform.modules.iam.mapper.PositionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private final ObjectProvider<EmployeeMapper> employeeMapperProvider;
    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<PositionMapper> positionMapperProvider;
    private final AuditTrailService auditTrailService;

    public EmployeeService(ObjectProvider<EmployeeMapper> employeeMapperProvider,
                           ObjectProvider<DepartmentMapper> departmentMapperProvider,
                           ObjectProvider<PositionMapper> positionMapperProvider,
                           AuditTrailService auditTrailService) {
        this.employeeMapperProvider = employeeMapperProvider;
        this.departmentMapperProvider = departmentMapperProvider;
        this.positionMapperProvider = positionMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public EmployeeResponse getEmployee(Long id) {
        EmployeeResponse response = requiredMapper().selectEmployeeById(id);
        if (response == null) {
            throw new BizException("IAM_EMPLOYEE_NOT_FOUND", "员工不存在");
        }
        return response;
    }

    public EmployeeResponse createEmployee(EmployeeSaveRequest request) {
        EmployeeEntity entity = new EmployeeEntity();
        apply(entity, request, requiredTenantId());
        requiredMapper().insert(entity);
        auditTrailService.record("iam", "employee", String.valueOf(entity.getId()), "CREATE", null, entity.getEmployeeNo(), true);
        return getEmployee(entity.getId());
    }

    public EmployeeResponse updateEmployee(Long id, EmployeeSaveRequest request) {
        EmployeeMapper mapper = requiredMapper();
        EmployeeEntity entity = requiredEntity(id);
        String before = entity.getDeptId() + ":" + entity.getPositionId();
        apply(entity, request, requiredTenantId());
        mapper.updateById(entity);
        EmployeeResponse response = getEmployee(id);
        auditTrailService.record("iam", "employee", String.valueOf(id), "UPDATE", before, response.deptId() + ":" + response.positionId(), true);
        return response;
    }

    public EmployeeResponse transferEmployee(Long id, EmployeeTransferRequest request) {
        EmployeeMapper mapper = requiredMapper();
        EmployeeEntity entity = requiredEntity(id);
        Long tenantId = requiredTenantId();
        validateDepartment(tenantId, request.deptId());
        validatePosition(tenantId, request.positionId());
        String before = entity.getDeptId() + ":" + entity.getPositionId();
        entity.setDeptId(request.deptId());
        entity.setPositionId(request.positionId());
        if (request.remark() != null && !request.remark().isBlank()) {
            entity.setRemark(request.remark());
        }
        mapper.updateById(entity);
        EmployeeResponse response = getEmployee(id);
        auditTrailService.record("iam", "employee_transfer", String.valueOf(id), "TRANSFER", before, response.deptId() + ":" + response.positionId(), true);
        return response;
    }

    public EmployeeResponse updateStatus(Long id, StatusUpdateRequest request) {
        EmployeeMapper mapper = requiredMapper();
        EmployeeEntity entity = requiredEntity(id);
        entity.setEmployeeStatus(request.status());
        mapper.updateById(entity);
        auditTrailService.record("iam", "employee", String.valueOf(id), "STATUS", null, request.status(), true);
        return getEmployee(id);
    }

    private void apply(EmployeeEntity entity, EmployeeSaveRequest request, Long tenantId) {
        validateDepartment(tenantId, request.deptId());
        validatePosition(tenantId, request.positionId());
        entity.setTenantId(tenantId);
        entity.setEmployeeNo(request.employeeNo());
        entity.setEmployeeName(request.employeeName());
        entity.setDeptId(request.deptId());
        entity.setPositionId(request.positionId());
        entity.setMobile(request.mobile());
        entity.setEmail(request.email());
        entity.setGender(request.gender());
        entity.setHireDate(request.hireDate());
        entity.setEmployeeStatus(request.employeeStatus());
        entity.setRemark(request.remark());
    }

    private void validateDepartment(Long tenantId, Long departmentId) {
        DepartmentEntity entity = requiredDepartmentMapper().selectById(departmentId);
        if (entity == null || isDeleted(entity) || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_EMPLOYEE_DEPARTMENT_NOT_FOUND", "所属部门不存在");
        }
    }

    private void validatePosition(Long tenantId, Long positionId) {
        PositionEntity entity = requiredPositionMapper().selectById(positionId);
        if (entity == null || isDeleted(entity) || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_EMPLOYEE_POSITION_NOT_FOUND", "所属岗位不存在");
        }
    }

    private EmployeeEntity requiredEntity(Long id) {
        EmployeeEntity entity = requiredMapper().selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null || isDeleted(entity) || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_EMPLOYEE_NOT_FOUND", "员工不存在");
        }
        return entity;
    }

    private EmployeeMapper requiredMapper() {
        EmployeeMapper mapper = employeeMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "员工写操作需要数据库连接");
        }
        return mapper;
    }

    private DepartmentMapper requiredDepartmentMapper() {
        DepartmentMapper mapper = departmentMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "部门校验需要数据库连接");
        }
        return mapper;
    }

    private PositionMapper requiredPositionMapper() {
        PositionMapper mapper = positionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "岗位校验需要数据库连接");
        }
        return mapper;
    }

    private Long requiredTenantId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.tenantId() == null) {
            throw new BizException("AUTH_UNAUTHORIZED", "Authentication is required");
        }
        return principal.tenantId();
    }

    private boolean isDeleted(EmployeeEntity entity) {
        return entity.getDeleted() != null && entity.getDeleted() == 1;
    }

    private boolean isDeleted(DepartmentEntity entity) {
        return entity.getDeleted() != null && entity.getDeleted() == 1;
    }

    private boolean isDeleted(PositionEntity entity) {
        return entity.getDeleted() != null && entity.getDeleted() == 1;
    }
}
