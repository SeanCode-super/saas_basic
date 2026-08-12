package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.dto.MenuPermissionResponse;
import com.saasbasics.platform.modules.iam.entity.MenuPermissionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface MenuPermissionMapper extends BaseMapper<MenuPermissionEntity> {

    @Delete("""
            DELETE FROM iam_menu_permission
            WHERE tenant_id = #{tenantId}
              AND subject_type = #{subjectType}
              AND subject_value = #{subjectValue}
            """)
    int deleteSubjectGrants(@Param("tenantId") Long tenantId,
                            @Param("subjectType") String subjectType,
                            @Param("subjectValue") String subjectValue);

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              menu_id AS menuId,
              menu_code AS menuCode,
              subject_type AS subjectType,
              subject_value AS subjectValue,
              CAST(button_codes_json AS CHAR) AS buttonCodesJson,
              status,
              remark
            FROM iam_menu_permission
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<MenuPermissionResponse> selectPermissionList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              menu_id AS menuId,
              menu_code AS menuCode,
              subject_type AS subjectType,
              subject_value AS subjectValue,
              CAST(button_codes_json AS CHAR) AS buttonCodesJson,
              status,
              remark
            FROM iam_menu_permission
            WHERE id = #{id}
              AND deleted = 0
            """)
    MenuPermissionResponse selectPermissionById(@Param("id") Long id);
}
