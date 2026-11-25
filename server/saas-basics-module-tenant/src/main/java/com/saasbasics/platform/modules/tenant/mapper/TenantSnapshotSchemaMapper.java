package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantSnapshotSchemaMapper {
    @InterceptorIgnore(tenantLine = "1")
    String storageEngine(@Param("tableName") String tableName);
}
