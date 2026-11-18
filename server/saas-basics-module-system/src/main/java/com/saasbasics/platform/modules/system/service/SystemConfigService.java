package com.saasbasics.platform.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import com.saasbasics.platform.modules.audit.service.AuditTrailService;
import com.saasbasics.platform.modules.system.dto.SystemConfigResponse;
import com.saasbasics.platform.modules.system.dto.SystemConfigSaveRequest;
import com.saasbasics.platform.modules.system.entity.SystemConfigEntity;
import com.saasbasics.platform.modules.system.mapper.SystemConfigMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Long tenantId = resolveCurrentTenantId();

        List<SystemConfigEntity> list = systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfigEntity>()
                        .eq(SystemConfigEntity::getTenantId, tenantId)
                        .eq(SystemConfigEntity::getDeleted, 0)
                        .orderByAsc(SystemConfigEntity::getConfigGroup, SystemConfigEntity::getConfigKey));

        if (list.isEmpty() && tenantId > 1L) {
            seedFromPlatform(systemConfigMapper, tenantId);
            list = systemConfigMapper.selectList(
                    new LambdaQueryWrapper<SystemConfigEntity>()
                            .eq(SystemConfigEntity::getTenantId, tenantId)
                            .eq(SystemConfigEntity::getDeleted, 0)
                            .orderByAsc(SystemConfigEntity::getConfigGroup, SystemConfigEntity::getConfigKey));
        }

        return list.stream()
                .map(this::toResponse)
                .toList();
    }

    public String getConfigValueWithFallback(String configKey, String defaultValue) {
        if (configKey == null || configKey.isBlank()) {
            return defaultValue;
        }
        SystemConfigMapper mapper = systemConfigMapperProvider.getIfAvailable();
        if (mapper == null) {
            return defaultValue;
        }
        Long tenantId = resolveCurrentTenantId();
        String trimmedKey = configKey.trim();
        SystemConfigEntity found = mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getTenantId, tenantId)
                .eq(SystemConfigEntity::getConfigKey, trimmedKey)
                .eq(SystemConfigEntity::getStatus, "ENABLED")
                .eq(SystemConfigEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (found == null && !Objects.equals(1L, tenantId)) {
            try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                    TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Fallback read platform system config")) {
                found = mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                        .eq(SystemConfigEntity::getTenantId, 1L)
                        .eq(SystemConfigEntity::getConfigKey, trimmedKey)
                        .eq(SystemConfigEntity::getStatus, "ENABLED")
                        .eq(SystemConfigEntity::getDeleted, 0)
                        .last("LIMIT 1"));
            }
        }
        return (found != null && found.getConfigValue() != null) ? found.getConfigValue() : defaultValue;
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
        entity.setTenantId(resolveCurrentTenantId());
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
        Long tenantId = resolveCurrentTenantId();
        SystemConfigEntity existing = mapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getTenantId, tenantId)
                .eq(SystemConfigEntity::getConfigKey, request.key())
                .eq(SystemConfigEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) {
            SystemConfigEntity entity = new SystemConfigEntity();
            entity.setTenantId(tenantId);
            apply(entity, request);
            mapper.insert(entity);
            auditTrailService.record("system", "system_config", String.valueOf(entity.getId()), "UPSERT_CREATE", request.key(), request.value(), true);
            return getConfig(entity.getId());
        }
        String prevValue = existing.getConfigValue();
        apply(existing, request);
        mapper.updateById(existing);
        auditTrailService.record("system", "system_config", String.valueOf(existing.getId()), "UPSERT_UPDATE", prevValue, request.value(), true);
        return getConfig(existing.getId());
    }

    @Transactional
    protected void seedFromPlatform(SystemConfigMapper mapper, Long tenantId) {
        List<SystemConfigEntity> defaults;
        try (TenantAccessContextHolder.Scope ignored = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.TENANT_BOOTSTRAP, "Clone baseline system configs")) {
            defaults = mapper.selectList(new LambdaQueryWrapper<SystemConfigEntity>()
                    .eq(SystemConfigEntity::getTenantId, 1L)
                    .eq(SystemConfigEntity::getDeleted, 0));
        }
        for (SystemConfigEntity src : defaults) {
            if (Boolean.TRUE.equals(src.getEncrypted())) {
                continue;
            }
            SystemConfigEntity copy = new SystemConfigEntity();
            copy.setTenantId(tenantId);
            copy.setConfigGroup(src.getConfigGroup());
            copy.setConfigKey(src.getConfigKey());
            copy.setConfigName(src.getConfigName());
            copy.setConfigValue(src.getConfigValue());
            copy.setValueType(src.getValueType());
            copy.setEncrypted(false);
            copy.setStatus(src.getStatus());
            copy.setRemark(src.getRemark());
            mapper.insert(copy);
        }
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
        if (entity.getTenantId() == null) {
            entity.setTenantId(resolveCurrentTenantId());
        }
        entity.setConfigGroup(request.group());
        entity.setConfigKey(request.key());
        entity.setConfigName(request.name());
        entity.setConfigValue(request.value());
        entity.setValueType(request.valueType());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private Long resolveCurrentTenantId() {
        TenantAccessContext ctx = TenantAccessContextHolder.current();
        if (ctx != null && ctx.tenantId() != null && ctx.tenantId() > 0L) {
            return ctx.tenantId();
        }
        AuthPrincipal principal = AuthContext.get();
        if (principal != null && principal.tenantId() != null && principal.tenantId() > 0L) {
            return principal.tenantId();
        }
        return 1L;
    }

    private SystemConfigMapper requiredMapper() {
        SystemConfigMapper mapper = systemConfigMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
