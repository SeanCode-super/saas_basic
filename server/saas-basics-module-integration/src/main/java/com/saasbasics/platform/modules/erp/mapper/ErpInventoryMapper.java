package com.saasbasics.platform.modules.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@DataScope(resource = "erp:inventory", orgColumn = "", unitColumn = "", userColumn = "")
public interface ErpInventoryMapper extends BaseMapper<ErpInventoryEntity> {
    void ensureBalance(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                       @Param("warehouseId") Long warehouseId);

    ErpInventoryEntity lockForUpdate(@Param("tenantId") Long tenantId, @Param("productId") Long productId,
                                    @Param("warehouseId") Long warehouseId);
}
