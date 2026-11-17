package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.SystemConfigResponse;
import com.saasbasics.platform.modules.system.dto.SystemConfigSaveRequest;
import com.saasbasics.platform.modules.system.entity.SystemConfigEntity;
import com.saasbasics.platform.modules.system.mapper.SystemConfigMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigService {

    private final ObjectProvider<SystemConfigMapper> systemConfigMapperProvider;
    private final AuditTrailService auditTrailService;

    public SystemConfigService(ObjectProvider<SystemConfigMapper> systemConfigMapperProvider,
                               AuditTrailService auditTrailService) {
        this.systemConfigMapperProvider = systemConfigMapperProvider;
        this.auditTrailService = auditTrailService;
    }

    public List<SystemConfigResponse> listConfigs() {
        SystemConfigMapper systemConfigMapper = requiredMapper();

        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getDeleted, 0)
                .orderByAsc(SystemConfigEntity::getConfigGroup, SystemConfigEntity::getConfigKey);

        return systemConfigMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SystemConfigResponse getConfig(Long id) {
        SystemConfigEntity entity = requiredMapper().selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("SYSTEM_CONFIG_NOT_FOUND", "System config not found");
        }
        return toResponse(entity);
    }

    public SystemConfigResponse createConfig(SystemConfigSaveRequest request) {
        SystemConfigEntity entity = new SystemConfigEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        SystemConfigResponse response = getConfig(entity.getId());
        auditTrailService.record("system", "system_config", String.valueOf(response.id()), "CREATE", request.key(), response.key(), true);
        return response;
    }

    public SystemConfigResponse updateConfig(Long id, SystemConfigSaveRequest request) {
        SystemConfigMapper mapper = requiredMapper();
        SystemConfigEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("SYSTEM_CONFIG_NOT_FOUND", "System config not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        SystemConfigResponse response = getConfig(id);
        auditTrailService.record("system", "system_config", String.valueOf(response.id()), "UPDATE", request.key(), response.key(), true);
        return response;
    }

    public SystemConfigResponse upsertConfigByKey(SystemConfigSaveRequest request) {
        SystemConfigMapper mapper = requiredMapper();
        Long tenantId = request.tenantId() != null ? request.tenantId() : 1L;
        SystemConfigEntity existing = mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getTenantId, tenantId)
                .eq(SystemConfigEntity::getConfigKey, request.key())
                .eq(SystemConfigEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) {
            SystemConfigEntity entity = new SystemConfigEntity();
            apply(entity, new SystemConfigSaveRequest(
                    tenantId, request.group(), request.key(), request.name(),
                    request.value(), request.valueType(), request.status(), request.remark()
            ));
            mapper.insert(entity);
            auditTrailService.record("system", "system_config", String.valueOf(entity.getId()), "UPSERT_CREATE", request.key(), request.value(), true);
            return getConfig(entity.getId());
        }
        String prevValue = existing.getConfigValue();
        apply(existing, new SystemConfigSaveRequest(
                tenantId, request.group(), request.key(), request.name(),
                request.value(), request.valueType(), request.status(), request.remark()
        ));
        mapper.updateById(existing);
        auditTrailService.record("system", "system_config", String.valueOf(existing.getId()), "UPSERT_UPDATE", prevValue, request.value(), true);
        return getConfig(existing.getId());
    }

    private SystemConfigResponse toResponse(SystemConfigEntity entity) {
        return new SystemConfigResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getConfigGroup(),
                entity.getConfigKey(),
                entity.getConfigName(),
                entity.getConfigValue(),
                entity.getValueType(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private void apply(SystemConfigEntity entity, SystemConfigSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setConfigGroup(request.group());
        entity.setConfigKey(request.key());
        entity.setConfigName(request.name());
        entity.setConfigValue(request.value());
        entity.setValueType(request.valueType());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private SystemConfigMapper requiredMapper() {
        SystemConfigMapper mapper = systemConfigMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
