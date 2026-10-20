package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantQuotaEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaEntity> {

    @Select("""
            SELECT *
            FROM plat_tenant_quota
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
            ORDER BY id ASC
            """)
    List<TenantQuotaEntity> selectByTenantId(@Param("tenantId") Long tenantId);
}
