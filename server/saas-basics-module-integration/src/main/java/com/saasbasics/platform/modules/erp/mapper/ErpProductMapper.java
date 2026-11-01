package com.saasbasics.platform.modules.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.erp.entity.ErpProductEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "erp:product", userColumn = "created_by")
public interface ErpProductMapper extends BaseMapper<ErpProductEntity> {
}
