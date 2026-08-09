package com.saasbasics.platform.common.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DataPermissionSqlSpec {

    private Long tenantId;
    private boolean allowAll;
    private boolean denyAll;
    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> employeeIds = new LinkedHashSet<>();
    private final Set<Long> departmentIds = new LinkedHashSet<>();
    private final Set<Long> positionIds = new LinkedHashSet<>();
    private final Set<Long> roleIds = new LinkedHashSet<>();

    public static DataPermissionSqlSpec allowAll(Long tenantId) {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        spec.setAllowAll(true);
        return spec;
    }

    public static DataPermissionSqlSpec denyAll(Long tenantId) {
        DataPermissionSqlSpec spec = new DataPermissionSqlSpec();
        spec.setTenantId(tenantId);
        spec.setDenyAll(true);
        return spec;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
    }

    public boolean isDenyAll() {
        return denyAll;
    }

    public void setDenyAll(boolean denyAll) {
        this.denyAll = denyAll;
    }

    public List<Long> getUserIds() {
        return new ArrayList<>(userIds);
    }

    public List<Long> getEmployeeIds() {
        return new ArrayList<>(employeeIds);
    }

    public List<Long> getDepartmentIds() {
        return new ArrayList<>(departmentIds);
    }

    public List<Long> getPositionIds() {
        return new ArrayList<>(positionIds);
    }

    public List<Long> getRoleIds() {
        return new ArrayList<>(roleIds);
    }

    public void addUserId(Long userId) {
        if (userId != null && userId > 0) {
            userIds.add(userId);
        }
    }

    public void addEmployeeId(Long employeeId) {
        if (employeeId != null && employeeId > 0) {
            employeeIds.add(employeeId);
        }
    }

    public void addDepartmentId(Long departmentId) {
        if (departmentId != null && departmentId > 0) {
            departmentIds.add(departmentId);
        }
    }

    public void addDepartmentIds(Iterable<Long> departmentIds) {
        if (departmentIds == null) {
            return;
        }
        departmentIds.forEach(this::addDepartmentId);
    }

    public void addPositionId(Long positionId) {
        if (positionId != null && positionId > 0) {
            positionIds.add(positionId);
        }
    }

    public void addPositionIds(Iterable<Long> positionIds) {
        if (positionIds == null) {
            return;
        }
        positionIds.forEach(this::addPositionId);
    }

    public void addRoleId(Long roleId) {
        if (roleId != null && roleId > 0) {
            roleIds.add(roleId);
        }
    }

    public void addRoleIds(Iterable<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        roleIds.forEach(this::addRoleId);
    }

    public boolean hasConstraints() {
        return !userIds.isEmpty()
                || !employeeIds.isEmpty()
                || !departmentIds.isEmpty()
                || !positionIds.isEmpty()
                || !roleIds.isEmpty();
    }
}
