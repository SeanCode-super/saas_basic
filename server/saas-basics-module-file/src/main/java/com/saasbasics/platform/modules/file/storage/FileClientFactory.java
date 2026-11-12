package com.saasbasics.platform.modules.file.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.mapper.FileStorageMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文件存储客户端工厂。
 * 严格基于已启用并配置完成的 file_storage 记录构建或缓存对应 FileClient；
 * 当不存在有效存储源时明确报错提示，而非静默写入未知位置。
 */
@Component
public class FileClientFactory {

    private final ObjectProvider<FileStorageMapper> fileStorageMapperProvider;
    private final String defaultLocalPath;
    private final Map<Long, FileClient> clientCache = new ConcurrentHashMap<>();

    public FileClientFactory(ObjectProvider<FileStorageMapper> fileStorageMapperProvider,
                             @Value("${saas.file.local-path:}") String defaultLocalPath) {
        this.fileStorageMapperProvider = fileStorageMapperProvider;
        this.defaultLocalPath = defaultLocalPath;
    }

    public FileClient getDefaultClient() {
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper != null) {
            AuthPrincipal principal = AuthContext.get();
            Long tenantId = principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
            List<FileStorageEntity> enabled = mapper.selectList(new LambdaQueryWrapper<FileStorageEntity>()
                    .eq(FileStorageEntity::getTenantId, tenantId)
                    .eq(FileStorageEntity::getStatus, "ENABLED")
                    .eq(FileStorageEntity::getDeleted, 0)
                    .orderByDesc(FileStorageEntity::getIsDefault)
                    .orderByAsc(FileStorageEntity::getId)
                    .last("LIMIT 1"));
            if (!enabled.isEmpty()) {
                return getClient(enabled.get(0));
            }
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源与默认上传目录");
        }
        if (defaultLocalPath != null && !defaultLocalPath.isBlank()) {
            return new LocalFileClient(defaultLocalPath, "/api/file/raw-download");
        }
        throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                "当前尚未配置可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源");
    }

    public FileClient getClient(FileStorageEntity entity) {
        if (entity == null || entity.getId() == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源与默认上传目录");
        }
        return clientCache.computeIfAbsent(entity.getId(), ignored -> createClient(entity));
    }

    public FileClient getClient(Long storageId) {
        if (storageId == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源");
        }
        return clientCache.computeIfAbsent(storageId, id -> {
            FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
            if (mapper != null) {
                FileStorageEntity entity = mapper.selectById(id);
                if (entity != null && (entity.getDeleted() == null || entity.getDeleted() == 0)) {
                    return createClient(entity);
                }
            }
            throw new BizException("FILE_STORAGE_NOT_FOUND", "指定的文件存储源不存在或已停用");
        });
    }

    public void evictClient(Long storageId) {
        if (storageId != null) {
            clientCache.remove(storageId);
        } else {
            clientCache.clear();
        }
    }

    public void evictAll() {
        clientCache.clear();
    }

    private FileClient createClient(FileStorageEntity entity) {
        String type = entity.getStorageType() != null ? entity.getStorageType().toUpperCase() : "LOCAL";
        if ("LOCAL".equals(type)) {
            String dir = firstNonBlank(entity.getBasePath(), entity.getEndpoint(), defaultLocalPath);
            if (dir == null || dir.isBlank()) {
                throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                        "本地存储源 [" + entity.getStorageName() + "] 尚未配置存储根目录 (basePath)");
            }
            return new LocalFileClient(dir, firstNonBlank(entity.getPublicBaseUrl(), "/api/file/raw-download", null));
        }
        String endpoint = entity.getEndpoint();
        String bucket = entity.getBucketDefault();
        if (endpoint == null || endpoint.isBlank() || bucket == null || bucket.isBlank()) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "对象存储源 [" + entity.getStorageName() + "] 缺少 Endpoint 或 Bucket 配置");
        }
        return new S3CompatibleFileClient(
                endpoint,
                bucket,
                entity.getAccessKey(),
                entity.getSecretCipher(),
                entity.getPublicBaseUrl()
        );
    }

    private static String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        if (third != null && !third.isBlank()) return third.trim();
        return null;
    }
}
