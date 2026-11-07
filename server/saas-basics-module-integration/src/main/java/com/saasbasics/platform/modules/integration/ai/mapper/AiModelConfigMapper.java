package com.saasbasics.platform.modules.integration.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.integration.ai.entity.AiModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfigEntity> {
}
