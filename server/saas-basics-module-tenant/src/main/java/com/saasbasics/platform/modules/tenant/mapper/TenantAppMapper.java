package com.saasbasics.platform.modules.tenant.mapper;

import com.saasbasics.platform.common.tenant.TenantApplication.Endpoint;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.tenant.entity.TenantAppEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantAppMapper extends BaseMapper<TenantAppEntity> {

    int resetInstalledExtensions(@Param("tenantId") Long tenantId);
    void disableExtensionResources(@Param("tenantId") long tenantId, @Param("prefix") String prefix);
    void enableEndpoint(@Param("tenantId") long tenantId, @Param("permission") String permission, @Param("endpoint") Endpoint endpoint);
    void disableEndpoint(@Param("tenantId") long tenantId, @Param("permission") String permission);
    void disableMenu(@Param("tenantId") long tenantId, @Param("menuCode") String menuCode);
    void enableMenu(@Param("tenantId") long tenantId, @Param("appCode") String appCode, @Param("menuCode") String menuCode,
                    @Param("name") String name, @Param("component") String component);
    void grantGeneratedMenu(@Param("tenantId") long tenantId, @Param("tenantCode") String tenantCode,
                            @Param("code") String code, @Param("actor") long actor);
    void grantGeneratedActions(@Param("tenantId") long tenantId, @Param("tenantCode") String tenantCode,
                               @Param("code") String code, @Param("actor") long actor);
}
