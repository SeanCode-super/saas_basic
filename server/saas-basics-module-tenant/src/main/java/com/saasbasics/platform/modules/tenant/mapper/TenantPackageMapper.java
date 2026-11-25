package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantPackageMapper extends BaseMapper<TenantPackageEntity> {

    List<TenantPackageResponse> selectPackageList();

    TenantPackageResponse selectPackageById(@Param("id") Long id);
}
