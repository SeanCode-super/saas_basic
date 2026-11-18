package com.saasbasics.platform.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.common.auth.DataScope;
import com.saasbasics.platform.modules.workflow.entity.WorkflowTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(resource = "workflow:task", orgColumn = "", unitColumn = "", userColumn = "assignee_id")
public interface WorkflowTaskMapper extends BaseMapper<WorkflowTaskEntity> {
}
