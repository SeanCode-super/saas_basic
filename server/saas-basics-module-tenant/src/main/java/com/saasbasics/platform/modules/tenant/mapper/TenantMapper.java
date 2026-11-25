package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    List<TenantResponse> selectTenantPage();

    TenantResponse selectTenantById(@Param("id") Long id);
}
