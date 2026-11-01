package com.saasbasics.platform.modules.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.crm.entity.CrmClueEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "crm:clue", userColumn = "owner_user_id")
public interface CrmClueMapper extends BaseMapper<CrmClueEntity> {
}
