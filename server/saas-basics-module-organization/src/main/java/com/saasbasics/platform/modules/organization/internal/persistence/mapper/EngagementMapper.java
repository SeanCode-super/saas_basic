package com.saasbasics.platform.modules.organization.internal.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.EngagementEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EngagementMapper extends BaseMapper<EngagementEntity> {

    @Select("SELECT id FROM org_engagement WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
