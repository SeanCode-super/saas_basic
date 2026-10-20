package com.saasbasics.platform.modules.codegen.mapper;

import com.saasbasics.platform.modules.codegen.dto.DbColumnResponse;
import com.saasbasics.platform.modules.codegen.dto.DbTableResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CodegenSchemaMapper {

    @Select("""
            SELECT
              table_name AS tableName,
              table_comment AS tableComment,
              DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
            FROM information_schema.tables
            WHERE table_schema = (SELECT DATABASE())
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
            WHERE table_schema = (SELECT DATABASE())
              AND table_name = #{tableName}
            ORDER BY ordinal_position ASC
            """)
    List<DbColumnResponse> selectDbColumns(@Param("tableName") String tableName);
}
