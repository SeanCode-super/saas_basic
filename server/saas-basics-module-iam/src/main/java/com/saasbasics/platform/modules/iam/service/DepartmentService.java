package com.saasbasics.platform.modules.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.dto.DepartmentSaveRequest;
import com.saasbasics.platform.modules.iam.dto.StatusUpdateRequest;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import com.saasbasics.platform.modules.iam.mapper.DepartmentMapper;
import com.saasbasics.platform.modules.iam.mapper.UserMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService {

    private final ObjectProvider<DepartmentMapper> departmentMapperProvider;
    private final ObjectProvider<UserMapper> userMapperProvider;
    private final AuditTrailService auditTrailService;

    public DepartmentService(ObjectProvider<DepartmentMapper> departmentMapperProvider,
                             ObjectProvider<UserMapper> userMapperProvider,
                             AuditTrailService auditTrailService) {
        this.departmentMapperProvider = departmentMapperProvider;
        this.userMapperProvider = userMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public DepartmentResponse getDepartment(Long id) {
        DepartmentResponse response = requiredMapper().selectDepartmentById(id);
        if (response == null) {
            throw new BizException("IAM_DEPARTMENT_NOT_FOUND", "部门不存在");
        }
        return response;
    }

    public DepartmentResponse createDepartment(DepartmentSaveRequest request) {
        DepartmentMapper mapper = requiredMapper();
        Long tenantId = requiredTenantId();
        DepartmentEntity entity = new DepartmentEntity();
        apply(entity, request, tenantId, null);
        mapper.insert(entity);
        refreshHierarchy(entity.getId());
        auditTrailService.record("iam", "department", String.valueOf(entity.getId()), "CREATE", null, entity.getDeptCode(), true);
        return getDepartment(entity.getId());
    }

    public DepartmentResponse updateDepartment(Long id, DepartmentSaveRequest request) {
        DepartmentMapper mapper = requiredMapper();
        DepartmentEntity entity = requiredEntity(id);
        String before = entity.getDeptFullName();
        apply(entity, request, requiredTenantId(), id);
        mapper.updateById(entity);
        refreshHierarchy(id);
        DepartmentResponse response = getDepartment(id);
        auditTrailService.record("iam", "department", String.valueOf(id), "UPDATE", before, response.deptFullName(), true);
        return response;
    }

    public DepartmentResponse updateStatus(Long id, StatusUpdateRequest request) {
        DepartmentMapper mapper = requiredMapper();
        DepartmentEntity entity = requiredEntity(id);
        entity.setStatus(request.status());
        mapper.updateById(entity);
        auditTrailService.record("iam", "department", String.valueOf(id), "STATUS", null, request.status(), true);
        return getDepartment(id);
    }

    private void apply(DepartmentEntity entity, DepartmentSaveRequest request, Long tenantId, Long currentId) {
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        DepartmentEntity parent = validateParent(tenantId, parentId, currentId);
        validateLeaderUser(tenantId, request.leaderUserId());
        entity.setTenantId(tenantId);
        entity.setParentId(parentId);
        entity.setDeptCode(request.deptCode().trim());
        entity.setDeptName(request.deptName().trim());
        entity.setDeptFullName(parent == null ? request.deptName().trim() : parent.getDeptFullName() + " / " + request.deptName().trim());
        entity.setLeaderUserId(request.leaderUserId() == null ? 0L : request.leaderUserId());
        entity.setStatus(request.status());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
    }

    private DepartmentEntity validateParent(Long tenantId, Long parentId, Long currentId) {
        if (parentId == null || parentId <= 0) {
            return null;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new BizException("IAM_DEPARTMENT_PARENT_INVALID", "上级部门不能选择自己");
        }
        DepartmentEntity parent = requiredMapper().selectById(parentId);
        if (parent == null || isDeleted(parent) || !tenantId.equals(parent.getTenantId())) {
            throw new BizException("IAM_DEPARTMENT_PARENT_NOT_FOUND", "上级部门不存在");
        }
        if (currentId != null) {
            if (isDescendant(currentId, parentId)) {
                throw new BizException("IAM_DEPARTMENT_PARENT_CYCLE", "不能把部门挂到自己的下级部门下面");
            }
        }
        return parent;
    }

    private boolean isDescendant(Long currentId, Long candidateParentId) {
        Long cursor = candidateParentId;
        while (cursor != null && cursor > 0) {
            if (currentId.equals(cursor)) {
                return true;
            }
            DepartmentEntity cursorEntity = requiredMapper().selectById(cursor);
            if (cursorEntity == null || isDeleted(cursorEntity)) {
                break;
            }
            cursor = cursorEntity.getParentId();
        }
        return false;
    }

    private void validateLeaderUser(Long tenantId, Long leaderUserId) {
        if (leaderUserId == null || leaderUserId <= 0) {
            return;
        }
        UserMapper userMapper = requiredUserMapper();
        UserEntity user = userMapper.selectById(leaderUserId);
        if (user == null || isDeleted(user) || !tenantId.equals(user.getTenantId())) {
            throw new BizException("IAM_DEPARTMENT_LEADER_NOT_FOUND", "负责人账号不存在");
        }
    }

    private void refreshHierarchy(Long departmentId) {
        DepartmentMapper mapper = requiredMapper();
        DepartmentEntity entity = requiredEntity(departmentId);
        DepartmentEntity parent = entity.getParentId() == null || entity.getParentId() <= 0 ? null : requiredEntity(entity.getParentId());
        String fullName = parent == null ? entity.getDeptName() : parent.getDeptFullName() + " / " + entity.getDeptName();
        String treePath = parent == null ? "/" + entity.getId() + "/" : parent.getTreePath() + entity.getId() + "/";
        boolean changed = !fullName.equals(entity.getDeptFullName()) || !treePath.equals(entity.getTreePath());
        if (changed) {
            entity.setDeptFullName(fullName);
            entity.setTreePath(treePath);
            mapper.updateById(entity);
        }
        List<DepartmentEntity> children = mapper.selectList(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getTenantId, entity.getTenantId())
                .eq(DepartmentEntity::getParentId, entity.getId())
                .eq(DepartmentEntity::getDeleted, 0)
                .orderByAsc(DepartmentEntity::getSortNo, DepartmentEntity::getId));
        for (DepartmentEntity child : children) {
            refreshHierarchy(child.getId());
        }
    }

    private DepartmentEntity requiredEntity(Long id) {
        DepartmentEntity entity = requiredMapper().selectById(id);
        Long tenantId = requiredTenantId();
        if (entity == null || isDeleted(entity) || !tenantId.equals(entity.getTenantId())) {
            throw new BizException("IAM_DEPARTMENT_NOT_FOUND", "部门不存在");
        }
        return entity;
    }

    private DepartmentMapper requiredMapper() {
        DepartmentMapper mapper = departmentMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "部门写操作需要数据库连接");
        }
        return mapper;
    }

    private UserMapper requiredUserMapper() {
        UserMapper mapper = userMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "负责人校验需要数据库连接");
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

    private boolean isDeleted(DepartmentEntity entity) {
        return entity.getDeleted() != null && entity.getDeleted() == 1;
    }

    private boolean isDeleted(UserEntity entity) {
        return entity.getDeleted() != null && entity.getDeleted() == 1;
    }
}
