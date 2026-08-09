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
              iam_department.id,
              iam_department.tenant_id AS tenantId,
              iam_department.parent_id AS parentId,
              iam_department.dept_code AS deptCode,
              iam_department.dept_name AS deptName,
              iam_department.dept_full_name AS deptFullName,
              iam_department.tree_path AS treePath,
              GREATEST(
                1,
                LENGTH(COALESCE(iam_department.tree_path, CONCAT('/', iam_department.id, '/')))
                - LENGTH(REPLACE(COALESCE(iam_department.tree_path, CONCAT('/', iam_department.id, '/')), '/', ''))
                - 1
              ) AS treeLevel,
              iam_department.leader_user_id AS leaderUserId,
              COALESCE(leader.nickname, leader.username, '') AS leaderName,
              (
                SELECT COUNT(*)
                FROM iam_department child
                WHERE child.parent_id = iam_department.id
                  AND child.deleted = 0
                  AND child.tenant_id = iam_department.tenant_id
              ) AS childCount,
              (
                SELECT COUNT(*)
                FROM iam_employee employee
                WHERE employee.dept_id = iam_department.id
                  AND employee.deleted = 0
                  AND employee.tenant_id = iam_department.tenant_id
              ) AS employeeCount,
              iam_department.status,
              iam_department.sort_no AS sortNo,
              iam_department.remark
            FROM iam_department
            LEFT JOIN iam_user leader ON leader.id = iam_department.leader_user_id AND leader.deleted = 0
            WHERE iam_department.deleted = 0
              AND iam_department.tenant_id = #{spec.tenantId}
            <if test="spec.denyAll">
              AND 1 = 0
            </if>
            <if test="!spec.allowAll and !spec.denyAll">
              <trim prefix="AND (" suffix=")" prefixOverrides="OR ">
                <if test="spec.departmentIds != null and spec.departmentIds.size() > 0">
                  OR id IN
                  <foreach collection="spec.departmentIds" item="departmentId" open="(" separator="," close=")">
                    #{departmentId}
                  </foreach>
                </if>
                <if test="spec.roleIds != null and spec.roleIds.size() > 0">
                  OR EXISTS (
                    SELECT 1
                    FROM iam_employee e
                    INNER JOIN iam_user u ON u.employee_id = e.id AND u.deleted = 0
                    INNER JOIN iam_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                    WHERE e.dept_id = iam_department.id
                      AND e.deleted = 0
                      AND e.tenant_id = iam_department.tenant_id
                      AND ur.role_id IN
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
            ORDER BY iam_department.sort_no ASC, iam_department.id ASC
            </script>
            """)
    List<DepartmentResponse> selectDepartmentList(@Param("spec") DataPermissionSqlSpec spec);

    @Select("""
            SELECT
              iam_department.id,
              iam_department.tenant_id AS tenantId,
              iam_department.parent_id AS parentId,
              iam_department.dept_code AS deptCode,
              iam_department.dept_name AS deptName,
              iam_department.dept_full_name AS deptFullName,
              iam_department.tree_path AS treePath,
              GREATEST(
                1,
                LENGTH(COALESCE(iam_department.tree_path, CONCAT('/', iam_department.id, '/')))
                - LENGTH(REPLACE(COALESCE(iam_department.tree_path, CONCAT('/', iam_department.id, '/')), '/', ''))
                - 1
              ) AS treeLevel,
              iam_department.leader_user_id AS leaderUserId,
              COALESCE(leader.nickname, leader.username, '') AS leaderName,
              (
                SELECT COUNT(*)
                FROM iam_department child
                WHERE child.parent_id = iam_department.id
                  AND child.deleted = 0
                  AND child.tenant_id = iam_department.tenant_id
              ) AS childCount,
              (
                SELECT COUNT(*)
                FROM iam_employee employee
                WHERE employee.dept_id = iam_department.id
                  AND employee.deleted = 0
                  AND employee.tenant_id = iam_department.tenant_id
              ) AS employeeCount,
              iam_department.status,
              iam_department.sort_no AS sortNo,
              iam_department.remark
            FROM iam_department
            LEFT JOIN iam_user leader ON leader.id = iam_department.leader_user_id AND leader.deleted = 0
            WHERE iam_department.id = #{id}
              AND iam_department.deleted = 0
            """)
    DepartmentResponse selectDepartmentById(@Param("id") Long id);
}
