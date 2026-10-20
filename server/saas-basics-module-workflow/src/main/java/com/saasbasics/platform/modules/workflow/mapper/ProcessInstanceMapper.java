package com.saasbasics.platform.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.workflow.entity.ProcessInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstanceEntity> {
}
