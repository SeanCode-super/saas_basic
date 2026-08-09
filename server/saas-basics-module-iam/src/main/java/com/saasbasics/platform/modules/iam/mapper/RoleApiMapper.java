package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.RoleApiEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleApiMapper extends BaseMapper<RoleApiEntity> {

    @Select("""
            SELECT api_resource_id
            FROM iam_role_api
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
              AND deleted = 0
            ORDER BY id DESC
            """)
    List<Long> selectApiResourceIdsByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    @Select("""
            SELECT DISTINCT ar.resource_code
            FROM iam_user_role ur
            INNER JOIN iam_role r ON r.id = ur.role_id
              AND r.deleted = 0
              AND r.status = 'ENABLED'
            INNER JOIN iam_role_api ra ON ra.role_id = ur.role_id
              AND ra.tenant_id = ur.tenant_id
              AND ra.deleted = 0
            INNER JOIN iam_api_resource ar ON ar.id = ra.api_resource_id
              AND ar.deleted = 0
              AND ar.status = 'ENABLED'
            WHERE ur.tenant_id = #{tenantId}
              AND ur.user_id = #{userId}
              AND ur.deleted = 0
              AND (ur.expire_at IS NULL OR ur.expire_at > #{now})
            ORDER BY ar.resource_code ASC
            """)
    List<String> selectPermissionCodesByUserId(@Param("tenantId") Long tenantId,
                                               @Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM iam_role_api
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    int deleteAssignments(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
