package com.saasbasics.platform.modules.organization.internal.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.EngagementEntity;
import org.apache.ibatis.annotations.Param;

public interface EngagementMapper extends BaseMapper<EngagementEntity> {

    Long lockById(@Param("id") Long id);
}
