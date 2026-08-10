package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.UserResponse;
import com.saasbasics.platform.modules.iam.entity.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM iam_user WHERE tenant_id = #{tenantId} AND public_id = #{publicId} AND deleted = 0 LIMIT 1")
    UserEntity selectByTenantAndPublicId(@Param("tenantId") Long tenantId, @Param("publicId") String publicId);

    @Select("""
            <script>
            SELECT
              u.id,
              u.public_id AS publicId,
              u.tenant_id AS tenantId,
              u.user_code AS userCode,
              u.username,
              u.nickname,
              u.employee_id AS employeeId,
              u.user_type AS userType,
              u.status,
              u.mobile,
              u.email,
              u.remark
            FROM iam_user u
            LEFT JOIN iam_employee e ON e.id = u.employee_id AND e.deleted = 0
            WHERE u.deleted = 0
              AND u.tenant_id = #{spec.tenantId}
            <if test="spec.denyAll">
              AND 1 = 0
            </if>
            <if test="!spec.allowAll and !spec.denyAll">
              <trim prefix="AND (" suffix=")" prefixOverrides="OR ">
                <if test="spec.userIds != null and spec.userIds.size() > 0">
                  OR u.id IN
                  <foreach collection="spec.userIds" item="userId" open="(" separator="," close=")">
                    #{userId}
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
                    FROM iam_user_role ur
                    WHERE ur.tenant_id = u.tenant_id
                      AND ur.user_id = u.id
                      AND ur.deleted = 0
                      AND ur.role_id IN
                      <foreach collection="spec.roleIds" item="roleId" open="(" separator="," close=")">
                        #{roleId}
                      </foreach>
                  )
                </if>
              </trim>
            </if>
            ORDER BY u.id DESC
            </script>
            """)
    List<UserResponse> selectUserList(@Param("spec") DataPermissionSqlSpec spec);

    @Select("""
            SELECT
              id,
              public_id AS publicId,
              tenant_id AS tenantId,
              user_code AS userCode,
              username,
              nickname,
              employee_id AS employeeId,
              user_type AS userType,
              status,
              mobile,
              email,
              remark
            FROM iam_user
            WHERE id = #{id}
              AND deleted = 0
            """)
    UserResponse selectUserById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM iam_user
            WHERE tenant_id = #{tenantId}
              AND username = #{username}
              AND deleted = 0
            LIMIT 1
            """)
    UserEntity selectByTenantAndUsername(@Param("tenantId") Long tenantId, @Param("username") String username);
}
