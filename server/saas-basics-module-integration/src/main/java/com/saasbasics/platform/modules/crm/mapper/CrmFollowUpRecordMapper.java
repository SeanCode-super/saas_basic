package com.saasbasics.platform.modules.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.crm.entity.CrmFollowUpRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "crm:follow-up", orgColumn = "", unitColumn = "dept_id", userColumn = "created_by")
public interface CrmFollowUpRecordMapper extends BaseMapper<CrmFollowUpRecordEntity> {
}
