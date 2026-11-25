package com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiAgentWorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAgentWorkflowMapper extends BaseMapper<AiAgentWorkflowEntity> {
}
