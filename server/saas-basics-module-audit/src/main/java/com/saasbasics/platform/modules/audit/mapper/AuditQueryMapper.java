package com.saasbasics.platform.modules.audit.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.saasbasics.platform.modules.audit.entity.SecBlacklistEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditQueryMapper {

    List<Map<String, Object>> selectHighRiskCatalog(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    List<Map<String, Object>> selectPlatformHighRiskCatalog();

    String selectAuditPolicyConfig(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    String selectPlatformAuditPolicyConfig();

    @InterceptorIgnore(dataPermission = "true")
    List<SecBlacklistEntity> selectTenantBlacklist(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    List<SecBlacklistEntity> selectPublicBlacklist();

    List<Map<String, Object>> selectUsernamesByIds(@Param("ids") List<Long> ids);
}
