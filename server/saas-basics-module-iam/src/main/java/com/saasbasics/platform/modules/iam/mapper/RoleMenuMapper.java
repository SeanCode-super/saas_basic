package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.RoleMenuEntity;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuEntity> {

    @Select("""
            <script>
            SELECT DISTINCT menu_id
            FROM iam_role_menu
            WHERE tenant_id = #{tenantId}
              AND role_id IN
              <foreach item='id' collection='roleIds' open='(' separator=',' close=')'>
                #{id}
              </foreach>
            </script>
            """)
    Set<Long> selectMenuIdsByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") Collection<Long> roleIds);

    @Select("""
            <script>
            SELECT DISTINCT m.menu_code
            FROM iam_menu m
            INNER JOIN iam_role_menu rm ON m.id = rm.menu_id
            WHERE rm.tenant_id = #{tenantId}
              AND rm.role_id IN
              <foreach item='id' collection='roleIds' open='(' separator=',' close=')'>
                #{id}
              </foreach>
              AND m.menu_type = 'BUTTON'
              AND m.status = 'ENABLED'
              AND m.deleted = 0
            </script>
            """)
    List<String> selectButtonCodesByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") Collection<Long> roleIds);

    @Select("""
            SELECT menu_id
            FROM iam_role_menu
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    List<Long> selectMenuIdsByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    @Delete("""
            DELETE FROM iam_role_menu
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    int deleteByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
