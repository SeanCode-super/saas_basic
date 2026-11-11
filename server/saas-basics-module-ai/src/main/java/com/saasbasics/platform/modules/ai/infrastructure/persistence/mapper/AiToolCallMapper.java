package com.saasbasics.platform.modules.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.ai.infrastructure.persistence.entity.AiToolCallEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiToolCallMapper extends BaseMapper<AiToolCallEntity> {
}
