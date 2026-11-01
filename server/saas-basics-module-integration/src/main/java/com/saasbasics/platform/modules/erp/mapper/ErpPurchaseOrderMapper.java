package com.saasbasics.platform.modules.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.erp.entity.ErpPurchaseOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购订单数据访问层，绑定行级数据权限拦截器
 */
@Mapper
@DataScope(resource = "erp:order", userColumn = "created_by")
public interface ErpPurchaseOrderMapper extends BaseMapper<ErpPurchaseOrderEntity> {
}
