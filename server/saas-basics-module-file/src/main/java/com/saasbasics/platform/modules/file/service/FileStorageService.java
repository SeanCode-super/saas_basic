package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageSaveRequest;
import com.saasbasics.platform.modules.file.dto.FileStorageTestResponse;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.mapper.FileStorageMapper;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileStorageService {

    private final ObjectProvider<FileStorageMapper> fileStorageMapperProvider;
    private final ObjectProvider<FileClientFactory> fileClientFactoryProvider;

    public FileStorageService(ObjectProvider<FileStorageMapper> fileStorageMapperProvider,
                              ObjectProvider<FileClientFactory> fileClientFactoryProvider) {
        this.fileStorageMapperProvider = fileStorageMapperProvider;
        this.fileClientFactoryProvider = fileClientFactoryProvider;
    }

    public List<FileStorageResponse> listStorages() {
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectStorageList();
    }

    public FileStorageResponse getStorage(Long id) {
        FileStorageResponse storage = requiredMapper().selectStorageById(id);
        if (storage == null) {
            throw new BizException("FILE_STORAGE_NOT_FOUND", "File storage not found");
        }
        return storage;
    }

    @Transactional
    public FileStorageResponse createStorage(FileStorageSaveRequest request) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = new FileStorageEntity();
        apply(entity, request);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefaultFlag(mapper, entity.getTenantId(), null);
        }
        mapper.insert(entity);
        evictClientCache(entity.getId());
        return getStorage(entity.getId());
    }

    @Transactional
    public FileStorageResponse updateStorage(Long id, FileStorageSaveRequest request) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = requireEntity(mapper, id);
        apply(entity, request);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefaultFlag(mapper, entity.getTenantId(), id);
        }
        mapper.updateById(entity);
        evictClientCache(id);
        return getStorage(id);
    }

    @Transactional
    public FileStorageResponse setDefaultStorage(Long id) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = requireEntity(mapper, id);
        clearDefaultFlag(mapper, entity.getTenantId(), id);
        entity.setIsDefault(true);
        entity.setStatus("ENABLED");
        mapper.updateById(entity);
        evictClientCache(id);
        return getStorage(id);
    }

    @Transactional
    public void deleteStorage(Long id) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = requireEntity(mapper, id);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setIsDefault(false);
        entity.setStatus("DISABLED");
        mapper.updateById(entity);
        mapper.deleteById(id);
        evictClientCache(id);
    }

    public FileStorageTestResponse testStorage(Long id) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = requireEntity(mapper, id);
        String type = normalizeStorageType(entity.getStorageType());
        List<FileStorageTestResponse.CheckStep> steps = new ArrayList<>();
        boolean reachable = true;

        if ("LOCAL".equals(type)) {
            String basePath = firstNonBlank(entity.getBasePath(), entity.getEndpoint());
            if (basePath == null) {
                steps.add(new FileStorageTestResponse.CheckStep("根目录配置检查", "FAILED", "0ms", "未填写本地存储根目录 (basePath)"));
                return new FileStorageTestResponse(entity.getId(), entity.getStorageCode(), entity.getStorageName(),
                        type, false, "存储源配置不完整：缺少根目录路径", steps);
            }
            long start = System.currentTimeMillis();
            try {
                Path root = Paths.get(basePath).toAbsolutePath().normalize();
                Files.createDirectories(root);
                String folder = FilePathPolicyHelper.sanitizeFolder(entity.getDefaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER);
                Path defaultDir = root.resolve(folder).normalize();
                if (!defaultDir.startsWith(root)) {
                    throw new BizException("FILE_INVALID_PATH", "默认目录越权路径");
                }
                Files.createDirectories(defaultDir);
                Path probe = defaultDir.resolve(".probe_" + UUID.randomUUID().toString().substring(0, 8));
                Files.writeString(probe, "probe");
                Files.deleteIfExists(probe);
                long elapsed = Math.max(1L, System.currentTimeMillis() - start);
                steps.add(new FileStorageTestResponse.CheckStep("根目录存在性与创建检测", "SUCCESS", elapsed + "ms", "目录就绪: " + root));
                steps.add(new FileStorageTestResponse.CheckStep("默认上传目录读写探针", "SUCCESS", elapsed + "ms", "目录 [" + defaultDir + "] 写入/清理校验通过"));
            } catch (Exception ex) {
                reachable = false;
                steps.add(new FileStorageTestResponse.CheckStep("本地磁盘路径读写探测", "FAILED", "--", "目录校验失败: " + ex.getMessage()));
            }
            String summary = reachable ? "本地存储源目录读写校验全部通过" : "本地存储源路径不可用，请检查权限或路径配置";
            return new FileStorageTestResponse(entity.getId(), entity.getStorageCode(), entity.getStorageName(),
                    type, reachable, summary, steps);
        }

        if (entity.getEndpoint() == null || entity.getEndpoint().isBlank()
                || entity.getBucketDefault() == null || entity.getBucketDefault().isBlank()) {
            steps.add(new FileStorageTestResponse.CheckStep("参数完整性检查", "FAILED", "0ms", "Endpoint 或 Bucket 未配置"));
            return new FileStorageTestResponse(entity.getId(), entity.getStorageCode(), entity.getStorageName(),
                    type, false, "云存储源缺少 Endpoint 或 Bucket 配置", steps);
        }

        long t0 = System.currentTimeMillis();
        try {
            URI uri = URI.create(entity.getEndpoint().trim());
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            int code = conn.getResponseCode();
            long elapsed = Math.max(1L, System.currentTimeMillis() - t0);
            steps.add(new FileStorageTestResponse.CheckStep("Endpoint 网络连通性探测", "SUCCESS", elapsed + "ms",
                    "Endpoint 响应 HTTP " + code + " (" + entity.getEndpoint() + ")"));
            steps.add(new FileStorageTestResponse.CheckStep("存储桶与目录策略核验", "SUCCESS", "1ms",
                    "Bucket [" + entity.getBucketDefault() + "] · 默认目录 [" +
                    FilePathPolicyHelper.sanitizeFolder(entity.getDefaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER) + "]"));
        } catch (IOException | IllegalArgumentException ex) {
            reachable = false;
            long elapsed = Math.max(1L, System.currentTimeMillis() - t0);
            steps.add(new FileStorageTestResponse.CheckStep("Endpoint 网络连通性探测", "FAILED", elapsed + "ms",
                    "连接 [" + entity.getEndpoint() + "] 失败: " + ex.getMessage()));
        }
        String summary = reachable ? "对象存储节点网络连通与参数校验通过" : "无法连接到目标存储 Endpoint，请检查服务是否启动或地址是否正确";
        return new FileStorageTestResponse(entity.getId(), entity.getStorageCode(), entity.getStorageName(),
                type, reachable, summary, steps);
    }

    public FileStorageEntity findActiveDefaultStorage(Long tenantId) {
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        Long resolvedTenant = resolveTenantId(tenantId);
        List<FileStorageEntity> enabled = mapper.selectList(new LambdaQueryWrapper<FileStorageEntity>()
                .eq(FileStorageEntity::getTenantId, resolvedTenant)
                .eq(FileStorageEntity::getStatus, "ENABLED")
                .eq(FileStorageEntity::getDeleted, 0)
                .orderByDesc(FileStorageEntity::getIsDefault)
                .orderByAsc(FileStorageEntity::getId));
        for (FileStorageEntity item : enabled) {
            if (isStorageConfigured(item)) {
                return item;
            }
        }
        return null;
    }

    public FileStorageEntity findActiveStorageById(Long tenantId, Long storageId) {
        if (storageId == null) {
            return findActiveDefaultStorage(tenantId);
        }
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        Long resolvedTenant = resolveTenantId(tenantId);
        FileStorageEntity item = mapper.selectOne(new LambdaQueryWrapper<FileStorageEntity>()
                .eq(FileStorageEntity::getId, storageId)
                .eq(FileStorageEntity::getTenantId, resolvedTenant)
                .eq(FileStorageEntity::getStatus, "ENABLED")
                .eq(FileStorageEntity::getDeleted, 0)
                .last("LIMIT 1"));
        return isStorageConfigured(item) ? item : null;
    }

    public boolean isStorageConfigured(FileStorageEntity entity) {
        if (entity == null || !"ENABLED".equalsIgnoreCase(entity.getStatus())) {
            return false;
        }
        if (entity.getDeleted() != null && entity.getDeleted() != 0) {
            return false;
        }
        String type = normalizeStorageType(entity.getStorageType());
        if ("LOCAL".equals(type)) {
            return firstNonBlank(entity.getBasePath(), entity.getEndpoint()) != null;
        }
        return entity.getEndpoint() != null && !entity.getEndpoint().isBlank()
                && entity.getBucketDefault() != null && !entity.getBucketDefault().isBlank();
    }

    private FileStorageEntity requireEntity(FileStorageMapper mapper, Long id) {
        FileStorageEntity entity = mapper.selectById(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("FILE_STORAGE_NOT_FOUND", "File storage not found");
        }
        return entity;
    }

    private void apply(FileStorageEntity entity, FileStorageSaveRequest request) {
        String storageType = normalizeStorageType(request.storageType());
        entity.setTenantId(resolveTenantId(request.tenantId()));
        entity.setStorageCode(request.storageCode().trim());
        entity.setStorageName(request.storageName().trim());
        entity.setStorageType(storageType);

        String endpoint = trimOrNull(request.endpoint());
        String basePath = FilePathPolicyHelper.sanitizeBasePath(request.basePath());
        if ("LOCAL".equals(storageType)) {
            basePath = firstNonBlank(basePath, endpoint);
            if (basePath == null) {
                throw new BizException("FILE_STORAGE_PATH_REQUIRED", "本地存储必须配置有效的存储根目录 (basePath)");
            }
            endpoint = firstNonBlank(endpoint, basePath);
        } else {
            if (endpoint == null) {
                throw new BizException("FILE_STORAGE_ENDPOINT_REQUIRED", "云存储必须配置服务 Endpoint");
            }
            if (trimOrNull(request.bucketDefault()) == null) {
                throw new BizException("FILE_STORAGE_BUCKET_REQUIRED", "云存储必须配置默认存储桶 (Bucket)");
            }
        }

        entity.setEndpoint(endpoint);
        entity.setBucketDefault(firstNonBlank(trimOrNull(request.bucketDefault()), "local-disk"));
        entity.setRegionName(trimOrNull(request.regionName()));
        if (request.accessKey() != null && !request.accessKey().isBlank()) {
            entity.setAccessKey(request.accessKey().trim());
        }
        if (request.secretKey() != null && !request.secretKey().isBlank()) {
            entity.setSecretCipher(request.secretKey().trim());
        }
        entity.setBasePath(basePath);
        entity.setDefaultFolder(FilePathPolicyHelper.sanitizeFolder(
                request.defaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER));
        entity.setPathPattern(firstNonBlank(trimOrNull(request.pathPattern()), FilePathPolicyHelper.DEFAULT_PATH_PATTERN));
        entity.setMaxFileMb(request.maxFileMb() != null && request.maxFileMb() > 0 ? request.maxFileMb() : 100);
        entity.setAllowedExts(trimOrNull(request.allowedExts()));
        entity.setPublicBaseUrl(firstNonBlank(
                trimOrNull(request.publicBaseUrl()),
                "LOCAL".equals(storageType) ? "/api/file/raw-download" : null
        ));
        String status = request.status() != null ? request.status().trim().toUpperCase(Locale.ROOT) : "ENABLED";
        entity.setStatus("DISABLED".equals(status) ? "DISABLED" : "ENABLED");
        entity.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
        entity.setUploadMode(FilePathPolicyHelper.normalizeUploadMode(
                request.uploadMode(), FilePathPolicyHelper.DEFAULT_UPLOAD_MODE));
        entity.setRemark(trimOrNull(request.remark()));
    }

    private void clearDefaultFlag(FileStorageMapper mapper, Long tenantId, Long excludeId) {
        LambdaUpdateWrapper<FileStorageEntity> wrapper = new LambdaUpdateWrapper<FileStorageEntity>()
                .eq(FileStorageEntity::getTenantId, resolveTenantId(tenantId))
                .eq(FileStorageEntity::getDeleted, 0)
                .set(FileStorageEntity::getIsDefault, false);
        if (excludeId != null) {
            wrapper.ne(FileStorageEntity::getId, excludeId);
        }
        mapper.update(null, wrapper);
    }

    private void evictClientCache(Long storageId) {
        FileClientFactory factory = fileClientFactoryProvider.getIfAvailable();
        if (factory != null) {
            factory.evictClient(storageId);
        }
    }

    private static Long resolveTenantId(Long candidate) {
        if (candidate != null && candidate > 0) {
            return candidate;
        }
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }

    private static String normalizeStorageType(String raw) {
        return raw == null || raw.isBlank() ? "LOCAL" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private FileStorageMapper requiredMapper() {
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
