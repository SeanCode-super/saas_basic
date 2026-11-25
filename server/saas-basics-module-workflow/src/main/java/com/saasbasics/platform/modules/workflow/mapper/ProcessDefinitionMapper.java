package com.saasbasics.platform.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.workflow.entity.ProcessDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcessDefinitionMapper extends BaseMapper<ProcessDefinitionEntity> {

    ProcessDefinitionEntity selectForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
