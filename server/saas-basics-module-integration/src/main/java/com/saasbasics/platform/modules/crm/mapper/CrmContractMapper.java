package com.saasbasics.platform.modules.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.crm.entity.CrmContractEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "crm:contract", userColumn = "created_by")
public interface CrmContractMapper extends BaseMapper<CrmContractEntity> {
}
