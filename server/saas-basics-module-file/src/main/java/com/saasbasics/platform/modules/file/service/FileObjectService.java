package com.saasbasics.platform.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FileObjectService {

    private final ObjectProvider<FileObjectMapper> fileObjectMapperProvider;

    public FileObjectService(ObjectProvider<FileObjectMapper> fileObjectMapperProvider) {
        this.fileObjectMapperProvider = fileObjectMapperProvider;
    }

    public List<FileObjectResponse> listObjects() {
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectObjectList();
    }

    public FileObjectEntity findByObjectKey(Long tenantId, String objectKey) {
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        if (mapper == null || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<FileObjectEntity> query = new LambdaQueryWrapper<FileObjectEntity>()
                .eq(FileObjectEntity::getObjectKey, objectKey)
                .eq(FileObjectEntity::getDeleted, 0)
                .orderByDesc(FileObjectEntity::getId)
                .last("LIMIT 1");
        if (tenantId != null) {
            query.eq(FileObjectEntity::getTenantId, tenantId);
        }
        return mapper.selectOne(query);
    }

    public FileObjectEntity recordUploadedFile(Long tenantId,
                                               Long storageId,
                                               String objectKey,
                                               String fileName,
                                               String fileExt,
                                               String contentType,
                                               Long fileSize,
                                               String bizType,
                                               String uploadMode,
                                               String targetDir,
                                               String storagePath,
                                               String visibility) {
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        FileObjectEntity entity = new FileObjectEntity();
        entity.setTenantId(tenantId != null ? tenantId : 1L);
        entity.setBucketId(1L);
        entity.setStorageId(storageId);
        entity.setObjectKey(objectKey);
        entity.setFileName(fileName);
        entity.setFileExt(fileExt);
        entity.setContentType(contentType);
        entity.setFileSize(fileSize);
        entity.setStoragePath(storagePath != null && !storagePath.isBlank() ? storagePath : objectKey);
        entity.setUploadMode(uploadMode);
        entity.setTargetDir(targetDir);
        entity.setVisibility(visibility != null && !visibility.isBlank() ? visibility : "PRIVATE");
        entity.setBizType(bizType);
        entity.setVersionNo(1);
        entity.setStatus("ACTIVE");
        if (mapper != null) {
            mapper.insert(entity);
        }
        return entity;
    }
}
