package com.saasbasics.platform.modules.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectResponse;
import com.saasbasics.platform.modules.codegen.entity.CodegenProjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CodegenProjectMapper extends BaseMapper<CodegenProjectEntity> {

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              project_code AS code,
              project_name AS name,
              base_package AS basePackage,
              module_prefix AS modulePrefix,
              output_mode AS outputMode,
              status,
              remark
            FROM gen_project
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    List<CodegenProjectResponse> selectProjectList();

    @Select("""
            SELECT
              id,
              tenant_id AS tenantId,
              project_code AS code,
              project_name AS name,
              base_package AS basePackage,
              module_prefix AS modulePrefix,
              output_mode AS outputMode,
              status,
              remark
            FROM gen_project
            WHERE id = #{id}
              AND deleted = 0
            """)
    CodegenProjectResponse selectProjectById(@Param("id") Long id);
}
