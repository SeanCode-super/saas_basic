package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.DepartmentResponse;
import com.saasbasics.platform.modules.iam.entity.DepartmentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {

    @Select("""
            <script>
            SELECT
              department_record.id,
              department_record.tenant_id AS tenantId,
              department_record.parent_id AS parentId,
              department_record.dept_code AS deptCode,
              department_record.dept_name AS deptName,
              department_record.dept_full_name AS deptFullName,
              department_record.tree_path AS treePath,
              GREATEST(
                1,
                LENGTH(COALESCE(department_record.tree_path, CONCAT('/', department_record.id, '/')))
                - LENGTH(REPLACE(COALESCE(department_record.tree_path, CONCAT('/', department_record.id, '/')), '/', ''))
                - 1
              ) AS treeLevel,
              department_record.leader_user_id AS leaderUserId,
              COALESCE(leader.nickname, leader.username, '') AS leaderName,
              (
                SELECT COUNT(*)
                FROM iam_department child
                WHERE child.parent_id = department_record.id
                  AND child.deleted = 0
                  AND child.tenant_id = department_record.tenant_id
              ) AS childCount,
              (
                SELECT COUNT(*)
                FROM iam_employee employee
                WHERE employee.dept_id = department_record.id
                  AND employee.deleted = 0
                  AND employee.tenant_id = department_record.tenant_id
              ) AS employeeCount,
              department_record.status,
              department_record.sort_no AS sortNo,
              department_record.remark
            FROM iam_department department_record
            LEFT JOIN iam_user leader
              ON leader.id = department_record.leader_user_id
             AND leader.tenant_id = department_record.tenant_id
             AND leader.deleted = 0
            WHERE department_record.deleted = 0
              AND department_record.tenant_id = #{spec.tenantId}
            <if test="spec.denyAll">
              AND 1 = 0
            </if>
            <if test="!spec.allowAll and !spec.denyAll">
              <trim prefix="AND (" suffix=")" prefixOverrides="OR ">
                <if test="spec.departmentIds != null and spec.departmentIds.size() > 0">
                  OR department_record.id IN
                  <foreach collection="spec.departmentIds" item="departmentId" open="(" separator="," close=")">
                    #{departmentId}
                  </foreach>
                </if>
                <if test="spec.roleIds != null and spec.roleIds.size() > 0">
                  OR EXISTS (
                    SELECT 1
                    FROM iam_employee role_employee
                    INNER JOIN iam_user role_user
                      ON role_user.employee_id = role_employee.id
                     AND role_user.tenant_id = role_employee.tenant_id
                     AND role_user.deleted = 0
                    INNER JOIN iam_user_role role_assignment
                      ON role_assignment.user_id = role_user.id
                     AND role_assignment.tenant_id = role_user.tenant_id
                     AND role_assignment.deleted = 0
                    WHERE role_employee.dept_id = department_record.id
                      AND role_employee.deleted = 0
                      AND role_employee.tenant_id = department_record.tenant_id
                      AND role_assignment.role_id IN
                      <foreach collection="spec.roleIds" item="roleId" open="(" separator="," close=")">
                        #{roleId}
                      </foreach>
                  )
                </if>
              </trim>
              <if test="(spec.departmentIds == null or spec.departmentIds.size() == 0) and (spec.roleIds == null or spec.roleIds.size() == 0)">
                AND 1 = 0
              </if>
            </if>
            ORDER BY department_record.sort_no ASC, department_record.id ASC
            </script>
            """)
    List<DepartmentResponse> selectDepartmentList(@Param("spec") DataPermissionSqlSpec spec);

    @Select("""
            SELECT
              department_record.id,
              department_record.tenant_id AS tenantId,
              department_record.parent_id AS parentId,
              department_record.dept_code AS deptCode,
              department_record.dept_name AS deptName,
              department_record.dept_full_name AS deptFullName,
              department_record.tree_path AS treePath,
              GREATEST(
                1,
                LENGTH(COALESCE(department_record.tree_path, CONCAT('/', department_record.id, '/')))
                - LENGTH(REPLACE(COALESCE(department_record.tree_path, CONCAT('/', department_record.id, '/')), '/', ''))
                - 1
              ) AS treeLevel,
              department_record.leader_user_id AS leaderUserId,
              COALESCE(leader.nickname, leader.username, '') AS leaderName,
              (
                SELECT COUNT(*)
                FROM iam_department child
                WHERE child.parent_id = department_record.id
                  AND child.deleted = 0
                  AND child.tenant_id = department_record.tenant_id
              ) AS childCount,
              (
                SELECT COUNT(*)
                FROM iam_employee employee
                WHERE employee.dept_id = department_record.id
                  AND employee.deleted = 0
                  AND employee.tenant_id = department_record.tenant_id
              ) AS employeeCount,
              department_record.status,
              department_record.sort_no AS sortNo,
              department_record.remark
            FROM iam_department department_record
            LEFT JOIN iam_user leader
              ON leader.id = department_record.leader_user_id
             AND leader.tenant_id = department_record.tenant_id
             AND leader.deleted = 0
            WHERE department_record.id = #{id}
              AND department_record.deleted = 0
            """)
    DepartmentResponse selectDepartmentById(@Param("id") Long id);
}
