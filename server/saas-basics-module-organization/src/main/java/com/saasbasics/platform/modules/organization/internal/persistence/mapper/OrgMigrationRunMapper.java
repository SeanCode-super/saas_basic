package com.saasbasics.platform.modules.organization.internal.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.organization.internal.persistence.entity.OrgMigrationRunEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgMigrationRunMapper extends BaseMapper<OrgMigrationRunEntity> {
}
