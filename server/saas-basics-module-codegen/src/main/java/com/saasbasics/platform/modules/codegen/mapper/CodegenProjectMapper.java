package com.saasbasics.platform.modules.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.codegen.dto.CodegenProjectResponse;
import com.saasbasics.platform.modules.codegen.dto.DbColumnResponse;
import com.saasbasics.platform.modules.codegen.dto.DbTableResponse;
import com.saasbasics.platform.modules.codegen.entity.CodegenProjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CodegenProjectMapper extends BaseMapper<CodegenProjectEntity> {

    @Select("""
            SELECT
              table_name AS tableName,
              table_comment AS tableComment,
              DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
            ORDER BY table_name ASC
            """)
    List<DbTableResponse> selectDbTables();

    @Select("""
            SELECT
              column_name AS columnName,
              data_type AS dataType,
              column_type AS columnType,
              column_comment AS columnComment,
              is_nullable AS isNullable,
              column_key AS columnKey,
              extra AS extra
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = #{tableName}
            ORDER BY ordinal_position ASC
            """)
    List<DbColumnResponse> selectDbColumns(@Param("tableName") String tableName);

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
