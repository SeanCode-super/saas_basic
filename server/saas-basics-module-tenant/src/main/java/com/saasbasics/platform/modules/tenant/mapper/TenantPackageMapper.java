package com.saasbasics.platform.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.dto.TenantPackageResponse;
import com.saasbasics.platform.modules.tenant.entity.TenantPackageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantPackageMapper extends BaseMapper<TenantPackageEntity> {

    @Select("""
            SELECT
              p.id,
              p.package_code AS packageCode,
              p.package_name AS packageName,
              p.package_type AS packageType,
              p.status,
              p.max_user_count AS maxUserCount,
              p.max_dept_count AS maxDeptCount,
              p.max_storage_mb AS maxStorageMb,
              p.max_api_per_minute AS maxApiPerMinute,
              p.feature_flags_json AS featureFlagsJson,
              p.sort_no AS sortNo,
              p.remark,
              (SELECT COUNT(*) FROM plat_tenant t WHERE t.package_id = p.id AND t.deleted = 0) AS activeTenantCount
            FROM plat_tenant_package p
            WHERE p.deleted = 0
            ORDER BY p.sort_no ASC, p.id ASC
            """)
    List<TenantPackageResponse> selectPackageList();

    @Select("""
            SELECT
              p.id,
              p.package_code AS packageCode,
              p.package_name AS packageName,
              p.package_type AS packageType,
              p.status,
              p.max_user_count AS maxUserCount,
              p.max_dept_count AS maxDeptCount,
              p.max_storage_mb AS maxStorageMb,
              p.max_api_per_minute AS maxApiPerMinute,
              p.feature_flags_json AS featureFlagsJson,
              p.sort_no AS sortNo,
              p.remark,
              (SELECT COUNT(*) FROM plat_tenant t WHERE t.package_id = p.id AND t.deleted = 0) AS activeTenantCount
            FROM plat_tenant_package p
            WHERE p.id = #{id} AND p.deleted = 0
            """)
    TenantPackageResponse selectPackageById(@Param("id") Long id);
}
