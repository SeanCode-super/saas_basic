package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.dto.TenantResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    @Select("""
            SELECT
              t.id,
              t.tenant_code AS tenantCode,
              t.tenant_name AS tenantName,
              t.package_id AS packageId,
              COALESCE(p.package_name, 'UNASSIGNED') AS packageName,
              t.status,
              t.isolation_mode AS isolationMode,
              t.remark AS remark
            FROM plat_tenant t
            LEFT JOIN plat_tenant_package p ON p.id = t.package_id AND p.deleted = 0
            WHERE t.deleted = 0
            ORDER BY t.id DESC
            """)
    List<TenantResponse> selectTenantPage();

    @Select("""
            SELECT
              t.id,
              t.tenant_code AS tenantCode,
              t.tenant_name AS tenantName,
              t.package_id AS packageId,
              COALESCE(p.package_name, 'UNASSIGNED') AS packageName,
              t.status,
              t.isolation_mode AS isolationMode,
              t.remark AS remark
            FROM plat_tenant t
            LEFT JOIN plat_tenant_package p ON p.id = t.package_id AND p.deleted = 0
            WHERE t.id = #{id}
              AND t.deleted = 0
            """)
    TenantResponse selectTenantById(@Param("id") Long id);
}
