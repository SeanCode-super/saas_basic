package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantAppMapper extends BaseMapper<TenantAppEntity> {
}
