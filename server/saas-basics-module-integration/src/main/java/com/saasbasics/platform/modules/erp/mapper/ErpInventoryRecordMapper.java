package com.saasbasics.platform.modules.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.erp.entity.ErpInventoryRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "erp:inventory-record", orgColumn = "", unitColumn = "", userColumn = "")
public interface ErpInventoryRecordMapper extends BaseMapper<ErpInventoryRecordEntity> {
}
