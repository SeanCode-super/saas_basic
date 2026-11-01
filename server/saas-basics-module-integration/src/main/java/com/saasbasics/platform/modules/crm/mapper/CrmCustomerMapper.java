package com.saasbasics.platform.modules.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.crm.entity.CrmCustomerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户关系数据访问层，绑定行级数据权限拦截器
 */
@Mapper
@DataScope(resource = "crm:customer", userColumn = "owner_user_id")
public interface CrmCustomerMapper extends BaseMapper<CrmCustomerEntity> {
}
