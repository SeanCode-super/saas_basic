package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataPermissionSqlSpec;
import com.saasbasics.platform.modules.iam.dto.PositionResponse;
import com.saasbasics.platform.modules.iam.entity.PositionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PositionMapper extends BaseMapper<PositionEntity> {

    @Select("""
            <script>
            SELECT
              p.id,
              p.tenant_id AS tenantId,
              p.position_code AS positionCode,
              p.position_name AS positionName,
              p.position_level AS positionLevel,
              (
                SELECT COUNT(*)
                FROM iam_employee e
                WHERE e.position_id = p.id
                  AND e.deleted = 0
                  AND e.tenant_id = p.tenant_id
              ) AS employeeCount,
              p.status,
              p.sort_no AS sortNo,
              p.remark
            FROM iam_position p
            WHERE p.deleted = 0
              AND p.tenant_id = #{spec.tenantId}
            <if test="spec.denyAll">
              AND 1 = 0
            </if>
            <if test="!spec.allowAll and !spec.denyAll">
              <trim prefix="AND (" suffix=")" prefixOverrides="OR ">
                <if test="spec.positionIds != null and spec.positionIds.size() > 0">
                  OR p.id IN
                  <foreach collection="spec.positionIds" item="positionId" open="(" separator="," close=")">
                    #{positionId}
                  </foreach>
                </if>
                <if test="spec.roleIds != null and spec.roleIds.size() > 0">
                  OR EXISTS (
                    SELECT 1
                    FROM iam_employee e
                    INNER JOIN iam_user u ON u.employee_id = e.id AND u.deleted = 0
                    INNER JOIN iam_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
                    WHERE e.position_id = p.id
                      AND e.deleted = 0
                      AND e.tenant_id = p.tenant_id
                      AND ur.role_id IN
                      <foreach collection="spec.roleIds" item="roleId" open="(" separator="," close=")">
                        #{roleId}
                      </foreach>
                  )
                </if>
              </trim>
              <if test="(spec.positionIds == null or spec.positionIds.size() == 0) and (spec.roleIds == null or spec.roleIds.size() == 0)">
                AND 1 = 0
              </if>
            </if>
            ORDER BY p.sort_no ASC, p.id ASC
            </script>
            """)
    List<PositionResponse> selectPositionList(@Param("spec") DataPermissionSqlSpec spec);

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              position_code AS positionCode,
              position_name AS positionName,
              position_level AS positionLevel,
              (
                SELECT COUNT(*)
                FROM iam_employee e
                WHERE e.position_id = iam_position.id
                  AND e.deleted = 0
                  AND e.tenant_id = iam_position.tenant_id
              ) AS employeeCount,
              status,
              sort_no AS sortNo,
              remark
            FROM iam_position
            WHERE id = #{id}
              AND deleted = 0
            """)
    PositionResponse selectPositionById(@Param("id") Long id);
}
