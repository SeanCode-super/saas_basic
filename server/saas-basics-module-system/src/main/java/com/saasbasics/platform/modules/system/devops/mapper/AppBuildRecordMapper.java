package com.saasbasics.platform.modules.system.devops.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.system.devops.entity.AppBuildRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AppBuildRecordMapper extends BaseMapper<AppBuildRecordEntity> {
}
