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
              CASE
                WHEN t.status = 'DISABLED' THEN 'DISABLED'
                WHEN t.status = 'SUSPENDED' THEN 'SUSPENDED'
                WHEN t.expire_at IS NOT NULL AND t.expire_at < NOW() THEN 'EXPIRED'
                WHEN t.expire_at IS NOT NULL AND DATEDIFF(t.expire_at, NOW()) <= 7 THEN 'EXPIRING'
                ELSE t.status
              END AS status,
              t.isolation_mode AS isolationMode,
              DATE_FORMAT(t.expire_at, '%Y-%m-%d %H:%i:%s') AS expireAt,
              CASE
                WHEN t.expire_at IS NULL THEN NULL
                ELSE DATEDIFF(t.expire_at, NOW())
              END AS daysRemaining,
              t.contact_name AS contactName,
              t.contact_mobile AS contactMobile,
              t.contact_email AS contactEmail,
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
              CASE
                WHEN t.status = 'DISABLED' THEN 'DISABLED'
                WHEN t.status = 'SUSPENDED' THEN 'SUSPENDED'
                WHEN t.expire_at IS NOT NULL AND t.expire_at < NOW() THEN 'EXPIRED'
                WHEN t.expire_at IS NOT NULL AND DATEDIFF(t.expire_at, NOW()) <= 7 THEN 'EXPIRING'
                ELSE t.status
              END AS status,
              t.isolation_mode AS isolationMode,
              DATE_FORMAT(t.expire_at, '%Y-%m-%d %H:%i:%s') AS expireAt,
              CASE
                WHEN t.expire_at IS NULL THEN NULL
                ELSE DATEDIFF(t.expire_at, NOW())
              END AS daysRemaining,
              t.contact_name AS contactName,
              t.contact_mobile AS contactMobile,
              t.contact_email AS contactEmail,
              t.remark AS remark
            FROM plat_tenant t
            LEFT JOIN plat_tenant_package p ON p.id = t.package_id AND p.deleted = 0
            WHERE t.id = #{id}
              AND t.deleted = 0
            """)
    TenantResponse selectTenantById(@Param("id") Long id);
}
