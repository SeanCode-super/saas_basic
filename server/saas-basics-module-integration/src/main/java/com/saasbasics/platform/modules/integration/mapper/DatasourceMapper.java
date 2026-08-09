package com.saasbasics.platform.modules.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.integration.entity.DatasourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasourceMapper extends BaseMapper<DatasourceEntity> {
}
