package com.saasbasics.platform.modules.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.codegen.entity.DynamicModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DynamicModelMapper extends BaseMapper<DynamicModelEntity> {
    DynamicModelEntity lockPublished(@org.apache.ibatis.annotations.Param("tenantId") long tenantId,
                                     @org.apache.ibatis.annotations.Param("id") long id);
}
