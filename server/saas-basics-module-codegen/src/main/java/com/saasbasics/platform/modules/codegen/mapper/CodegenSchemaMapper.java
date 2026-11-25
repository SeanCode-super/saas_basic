package com.saasbasics.platform.modules.codegen.mapper;

import com.saasbasics.platform.modules.codegen.dto.DbColumnResponse;
import com.saasbasics.platform.modules.codegen.dto.DbTableResponse;
import java.util.List;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodegenSchemaMapper {

    @InterceptorIgnore(tenantLine = "1")
    List<DbTableResponse> selectDbTables();

    @InterceptorIgnore(tenantLine = "1")
    List<DbColumnResponse> selectDbColumns(@Param("tableName") String tableName);
}
