package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaEntity> {

    Long lockQuotaLimit(@Param("tenantId") Long tenantId, @Param("quotaCode") String quotaCode);

    int countActiveDepartments(@Param("tenantId") Long tenantId);
}
