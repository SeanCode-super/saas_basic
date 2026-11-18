package com.saasbasics.platform.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.workflow.entity.ProcessInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "workflow:instance", orgColumn = "", unitColumn = "initiator_dept_id", userColumn = "initiator_id")
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstanceEntity> {
}
