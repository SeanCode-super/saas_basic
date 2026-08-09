package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileStorageResponse;
import com.saasbasics.platform.modules.file.dto.FileStorageSaveRequest;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.mapper.FileStorageMapper;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

    private final ObjectProvider<FileStorageMapper> fileStorageMapperProvider;

    public FileStorageService(ObjectProvider<FileStorageMapper> fileStorageMapperProvider) {
        this.fileStorageMapperProvider = fileStorageMapperProvider;
    }

    public List<FileStorageResponse> listStorages() {
        return requiredMapper().selectStorageList();
    }

    public FileStorageResponse getStorage(Long id) {
        FileStorageResponse storage = requiredMapper().selectStorageById(id);
        if (storage == null) {
            throw new BizException("FILE_STORAGE_NOT_FOUND", "File storage not found");
        }
        return storage;
    }

    public FileStorageResponse createStorage(FileStorageSaveRequest request) {
        FileStorageEntity entity = new FileStorageEntity();
        apply(entity, request);
        requiredMapper().insert(entity);
        return getStorage(entity.getId());
    }

    public FileStorageResponse updateStorage(Long id, FileStorageSaveRequest request) {
        FileStorageMapper mapper = requiredMapper();
        FileStorageEntity entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BizException("FILE_STORAGE_NOT_FOUND", "File storage not found");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return getStorage(id);
    }

    private void apply(FileStorageEntity entity, FileStorageSaveRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setStorageCode(request.storageCode());
        entity.setStorageName(request.storageName());
        entity.setStorageType(request.storageType());
        entity.setEndpoint(request.endpoint());
        entity.setBucketDefault(request.bucketDefault());
        entity.setPublicBaseUrl(request.publicBaseUrl());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private FileStorageMapper requiredMapper() {
        FileStorageMapper mapper = fileStorageMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
