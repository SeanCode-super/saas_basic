package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteResponse;
import com.saasbasics.platform.modules.file.dto.FileUploadRouteSaveRequest;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
import com.saasbasics.platform.modules.file.mapper.FileUploadRouteMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileUploadRouteService {

    private final ObjectProvider<FileUploadRouteMapper> routeMapperProvider;

    public FileUploadRouteService(ObjectProvider<FileUploadRouteMapper> routeMapperProvider) {
        this.routeMapperProvider = routeMapperProvider;
    }

    public List<FileUploadRouteResponse> listRoutes() {
        FileUploadRouteMapper mapper = routeMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectRouteList();
    }

    public FileUploadRouteEntity findMatchingRoute(Long tenantId, String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        FileUploadRouteMapper mapper = routeMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        String normalizedBizType = bizType.trim().toUpperCase(Locale.ROOT);
        Long resolvedTenant = resolveTenantId(tenantId);
        return mapper.selectOne(new LambdaQueryWrapper<FileUploadRouteEntity>()
                .eq(FileUploadRouteEntity::getTenantId, resolvedTenant)
                .eq(FileUploadRouteEntity::getBizType, normalizedBizType)
                .eq(FileUploadRouteEntity::getStatus, "ENABLED")
                .eq(FileUploadRouteEntity::getDeleted, 0)
                .orderByAsc(FileUploadRouteEntity::getPriority)
                .last("LIMIT 1"));
    }

    @Transactional
    public FileUploadRouteResponse createRoute(FileUploadRouteSaveRequest request) {
        FileUploadRouteMapper mapper = requiredMapper();
        Long tenantId = resolveTenantId(request.tenantId());
        String bizType = request.bizType().trim().toUpperCase(Locale.ROOT);
        long exists = mapper.selectCount(new LambdaQueryWrapper<FileUploadRouteEntity>()
                .eq(FileUploadRouteEntity::getTenantId, tenantId)
                .eq(FileUploadRouteEntity::getBizType, bizType)
                .eq(FileUploadRouteEntity::getDeleted, 0));
        if (exists > 0) {
            throw new BizException("FILE_UPLOAD_ROUTE_EXISTS",
                    "业务类型 [" + bizType + "] 已存在上传目录配置，请直接编辑原规则");
        }
        FileUploadRouteEntity entity = new FileUploadRouteEntity();
        apply(entity, request, tenantId);
        mapper.insert(entity);
        return mapper.selectRouteById(entity.getId());
    }

    @Transactional
    public FileUploadRouteResponse updateRoute(Long id, FileUploadRouteSaveRequest request) {
        FileUploadRouteMapper mapper = requiredMapper();
        FileUploadRouteEntity entity = requireEntity(mapper, id);
        apply(entity, request, resolveTenantId(request.tenantId()));
        mapper.updateById(entity);
        return mapper.selectRouteById(id);
    }

    @Transactional
    public void deleteRoute(Long id) {
        FileUploadRouteMapper mapper = requiredMapper();
        FileUploadRouteEntity entity = requireEntity(mapper, id);
        entity.setDeletedAt(LocalDateTime.now());
        mapper.updateById(entity);
        mapper.deleteById(id);
    }

    private void apply(FileUploadRouteEntity entity, FileUploadRouteSaveRequest request, Long tenantId) {
        entity.setTenantId(tenantId);
        entity.setStorageId(request.storageId());
        entity.setBizType(request.bizType().trim().toUpperCase(Locale.ROOT));
        entity.setRouteName(request.routeName().trim());
        entity.setUploadMode(FilePathPolicyHelper.normalizeUploadMode(request.uploadMode(), null));
        String targetDir = FilePathPolicyHelper.sanitizeFolder(request.targetDir(), null);
        if (targetDir == null) {
            throw new BizException("FILE_INVALID_PATH", "必须指定合法的目标存储目录 (targetDir)");
        }
        entity.setTargetDir(targetDir);
        entity.setPathPattern(request.pathPattern() != null && !request.pathPattern().isBlank()
                ? request.pathPattern().trim() : null);
        entity.setMaxFileMb(request.maxFileMb() != null && request.maxFileMb() > 0 ? request.maxFileMb() : null);
        entity.setAllowedExts(request.allowedExts() != null && !request.allowedExts().isBlank()
                ? request.allowedExts().trim() : null);
        entity.setPriority(request.priority() != null ? request.priority() : 100);
        String status = request.status() != null ? request.status().trim().toUpperCase(Locale.ROOT) : "ENABLED";
        entity.setStatus("DISABLED".equals(status) ? "DISABLED" : "ENABLED");
        entity.setRemark(request.remark() != null && !request.remark().isBlank() ? request.remark().trim() : null);
    }

    private FileUploadRouteEntity requireEntity(FileUploadRouteMapper mapper, Long id) {
        FileUploadRouteEntity entity = mapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("FILE_UPLOAD_ROUTE_NOT_FOUND", "上传目录路由配置不存在");
        }
        return entity;
    }

    private static Long resolveTenantId(Long candidate) {
        if (candidate != null && candidate > 0) {
            return candidate;
        }
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }

    private FileUploadRouteMapper requiredMapper() {
        FileUploadRouteMapper mapper = routeMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
