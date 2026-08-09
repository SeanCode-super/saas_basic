package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.MenuResponse;
import com.saasbasics.platform.modules.iam.entity.MenuEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              parent_id AS parentId,
              menu_type AS menuType,
              menu_code AS menuCode,
              menu_name AS menuName,
              route_path AS routePath,
              component_path AS componentPath,
              permission_code AS permissionCode,
              icon,
              visible,
              keep_alive AS keepAlive,
              sort_no AS sortNo,
              status,
              CAST(meta_json AS CHAR) AS metaJson,
              remark
            FROM iam_menu
            WHERE deleted = 0
            ORDER BY sort_no ASC, id ASC
            """)
    List<MenuResponse> selectMenuList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              parent_id AS parentId,
              menu_type AS menuType,
              menu_code AS menuCode,
              menu_name AS menuName,
              route_path AS routePath,
              component_path AS componentPath,
              permission_code AS permissionCode,
              icon,
              visible,
              keep_alive AS keepAlive,
              sort_no AS sortNo,
              status,
              CAST(meta_json AS CHAR) AS metaJson,
              remark
            FROM iam_menu
            WHERE id = #{id}
              AND deleted = 0
            """)
    MenuResponse selectMenuById(@Param("id") Long id);
}
