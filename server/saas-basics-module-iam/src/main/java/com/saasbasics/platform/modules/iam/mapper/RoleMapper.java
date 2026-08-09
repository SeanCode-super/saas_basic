package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.RoleResponse;
import com.saasbasics.platform.modules.iam.entity.RoleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              role_group_id AS roleGroupId,
              role_code AS roleCode,
              role_name AS roleName,
              role_type AS roleType,
              data_scope_type AS dataScopeType,
              status,
              is_system AS `system`,
              sort_no AS sortNo,
              remark
            FROM iam_role
            WHERE deleted = 0
            ORDER BY sort_no ASC, id DESC
            """)
    List<RoleResponse> selectRoleList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              role_group_id AS roleGroupId,
              role_code AS roleCode,
              role_name AS roleName,
              role_type AS roleType,
              data_scope_type AS dataScopeType,
              status,
              is_system AS `system`,
              sort_no AS sortNo,
              remark
            FROM iam_role
            WHERE id = #{id}
              AND deleted = 0
            """)
    RoleResponse selectRoleById(@Param("id") Long id);

    @Select("""
            SELECT
              id,
              tenant_id,
              role_group_id,
              role_code,
              role_name,
              role_type,
              data_scope_type,
              status,
              is_system,
              sort_no,
              created_by,
              created_at,
              updated_by,
              updated_at,
              deleted,
              deleted_at,
              version,
              remark
            FROM iam_role
            WHERE id = #{id}
              AND deleted = 0
            """)
    RoleEntity selectEntityById(@Param("id") Long id);
}
