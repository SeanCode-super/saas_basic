package com.saasbasics.platform.modules.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.codegen.entity.DynamicFieldEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DynamicFieldMapper extends BaseMapper<DynamicFieldEntity> {
}
