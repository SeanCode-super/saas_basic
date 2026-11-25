package com.saasbasics.platform.modules.iam.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saasbasics.platform.modules.iam.entity.PortalClientEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortalClientMapper extends BaseMapper<PortalClientEntity> {

    default PortalClientEntity selectByClientId(String clientId) {
        return selectOne(new LambdaQueryWrapper<PortalClientEntity>()
                .eq(PortalClientEntity::getClientId, clientId)
                .last("LIMIT 1"));
    }

    default PortalClientEntity selectByTenantCode(String tenantCode) {
        return selectOne(new LambdaQueryWrapper<PortalClientEntity>()
                .eq(PortalClientEntity::getTenantCode, tenantCode)
                .orderByDesc(PortalClientEntity::getIsDefault)
                .orderByAsc(PortalClientEntity::getId)
                .last("LIMIT 1"));
    }

    default PortalClientEntity selectDefaultClient() {
        return selectOne(new LambdaQueryWrapper<PortalClientEntity>()
                .eq(PortalClientEntity::getIsDefault, true)
                .eq(PortalClientEntity::getStatus, "ENABLED")
                .last("LIMIT 1"));
    }

    int clearDefaultClients(@Param("currentClientId") Long currentClientId);
}
