package com.saasbasics.platform.modules.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.erp.entity.ErpProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@DataScope(resource = "erp:product", orgColumn = "", unitColumn = "dept_id", userColumn = "created_by")
public interface ErpProductMapper extends BaseMapper<ErpProductEntity> {
    ErpProductEntity lockForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
