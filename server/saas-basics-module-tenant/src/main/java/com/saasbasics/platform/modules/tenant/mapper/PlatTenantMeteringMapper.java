package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.PlatTenantMeteringEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatTenantMeteringMapper extends BaseMapper<PlatTenantMeteringEntity> {

    int countActiveSeats(@Param("tenantId") Long tenantId);

    long sumStorageUsed(@Param("tenantId") Long tenantId);

    long countAuditCalls(@Param("tenantId") Long tenantId);

    long sumUsage(@Param("tenantId") long tenantId, @Param("type") String type, @Param("today") boolean today);

    long countTodayAuditCalls(@Param("tenantId") Long tenantId);
}
