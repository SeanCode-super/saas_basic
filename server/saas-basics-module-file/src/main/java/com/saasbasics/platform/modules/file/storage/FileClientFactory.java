package com.saasbasics.platform.modules.file.storage;

import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.mapper.FileStorageMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件存储客户端工厂。
 * 根据存储配置动态产出或缓存对应的 FileClient。
 */
@Component
public class FileClientFactory {

    private final ObjectProvider<FileStorageMapper> fileStorageMapperProvider;
    private final String defaultLocalPath;
    private final Map<Long, FileClient> clientCache = new ConcurrentHashMap<>();
    private volatile FileClient defaultClient;

    public FileClientFactory(ObjectProvider<FileStorageMapper> fileStorageMapperProvider,
                             @Value("${saas.file.local-path:./data/storage}") String defaultLocalPath) {
        this.fileStorageMapperProvider = fileStorageMapperProvider;
        this.defaultLocalPath = defaultLocalPath;
    }

    public FileClient getClient(Long storageId) {
        if (storageId == null) {
            return getDefaultClient();
        }
        return clientCache.computeIfAbsent(storageId, id -> {
            FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
            if (mapper != null) {
                FileStorageEntity entity = mapper.selectById(id);
                if (entity != null) {
                    return createClient(entity);
                }
            }
            return getDefaultClient();
        });
    }

    public FileClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (this) {
                if (defaultClient == null) {
                    defaultClient = new LocalFileClient(defaultLocalPath, "/api/file/raw-download");
                }
            }
        }
        return defaultClient;
    }

    private FileClient createClient(FileStorageEntity entity) {
        String type = entity.getStorageType() != null ? entity.getStorageType().toUpperCase() : "LOCAL";
        if ("LOCAL".equals(type)) {
            String dir = (entity.getEndpoint() != null && !entity.getEndpoint().isBlank())
                    ? entity.getEndpoint()
                    : defaultLocalPath;
            return new LocalFileClient(dir, entity.getPublicBaseUrl());
        }
        // MinIO, Aliyun OSS, Tencent COS, Huawei OBS, or standard AWS S3
        return new S3CompatibleFileClient(
                entity.getEndpoint(),
                entity.getBucketDefault(),
                null,
                null,
                entity.getPublicBaseUrl()
        );
    }
}
