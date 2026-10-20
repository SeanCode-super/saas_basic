package com.saasbasics.platform.modules.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.crm.entity.CrmCustomerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrmCustomerMapper extends BaseMapper<CrmCustomerEntity> {
}
