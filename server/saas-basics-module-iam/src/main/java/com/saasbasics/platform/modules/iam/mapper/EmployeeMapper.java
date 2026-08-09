package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.EmployeeResponse;
import com.saasbasics.platform.modules.iam.entity.EmployeeEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {

    @Select("""
            <script>
            SELECT
              e.id,
              e.tenant_id AS tenantId,
              e.employee_no AS employeeNo,
              e.employee_name AS employeeName,
              e.dept_id AS deptId,
              d.dept_name AS deptName,
              d.dept_full_name AS deptFullName,
              e.position_id AS positionId,
              p.position_name AS positionName,
              e.mobile,
              e.email,
              e.gender,
              e.hire_date AS hireDate,
              (
                SELECT COUNT(*)
                FROM iam_user u
                WHERE u.employee_id = e.id
                  AND u.deleted = 0
                  AND u.tenant_id = e.tenant_id
              ) AS boundUserCount,
              e.employee_status AS employeeStatus,
              e.remark
            FROM iam_employee e
            LEFT JOIN iam_department d ON d.id = e.dept_id AND d.deleted = 0
            LEFT JOIN iam_position p ON p.id = e.position_id AND p.deleted = 0
            WHERE e.deleted = 0
              AND e.tenant_id = #{spec.tenantId}
            <if test="spec.denyAll">
              AND 1 = 0
            </if>
            <if test="!spec.allowAll and !spec.denyAll">
              <trim prefix="AND (" suffix=")" prefixOverrides="OR ">
                <if test="spec.employeeIds != null and spec.employeeIds.size() > 0">
                  OR e.id IN
                  <foreach collection="spec.employeeIds" item="employeeId" open="(" separator="," close=")">
                    #{employeeId}
                  </foreach>
                </if>
                <if test="spec.departmentIds != null and spec.departmentIds.size() > 0">
                  OR e.dept_id IN
                  <foreach collection="spec.departmentIds" item="departmentId" open="(" separator="," close=")">
                    #{departmentId}
                  </foreach>
                </if>
                <if test="spec.positionIds != null and spec.positionIds.size() > 0">
                  OR e.position_id IN
                  <foreach collection="spec.positionIds" item="positionId" open="(" separator="," close=")">
                    #{positionId}
                  </foreach>
                </if>
                <if test="spec.roleIds != null and spec.roleIds.size() > 0">
                  OR EXISTS (
                    SELECT 1
                    FROM iam_user u
                    INNER JOIN iam_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                    WHERE u.employee_id = e.id
                      AND u.deleted = 0
                      AND u.tenant_id = e.tenant_id
                      AND ur.role_id IN
                      <foreach collection="spec.roleIds" item="roleId" open="(" separator="," close=")">
                        #{roleId}
                      </foreach>
                  )
                </if>
              </trim>
            </if>
            ORDER BY e.id ASC
            </script>
            """)
    List<EmployeeResponse> selectEmployeeList(@Param("spec") DataPermissionSqlSpec spec);

    @Select("""
            SELECT
              e.id,
              e.tenant_id AS tenantId,
              e.employee_no AS employeeNo,
              e.employee_name AS employeeName,
              e.dept_id AS deptId,
              d.dept_name AS deptName,
              d.dept_full_name AS deptFullName,
              e.position_id AS positionId,
              p.position_name AS positionName,
              e.mobile,
              e.email,
              e.gender,
              e.hire_date AS hireDate,
              (
                SELECT COUNT(*)
                FROM iam_user u
                WHERE u.employee_id = e.id
                  AND u.deleted = 0
                  AND u.tenant_id = e.tenant_id
              ) AS boundUserCount,
              e.employee_status AS employeeStatus,
              e.remark
            FROM iam_employee e
            LEFT JOIN iam_department d ON d.id = e.dept_id AND d.deleted = 0
            LEFT JOIN iam_position p ON p.id = e.position_id AND p.deleted = 0
            WHERE e.id = #{id}
              AND e.deleted = 0
            """)
    EmployeeResponse selectEmployeeById(@Param("id") Long id);
}
