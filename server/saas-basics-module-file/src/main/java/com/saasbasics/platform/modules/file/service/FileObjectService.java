package com.saasbasics.platform.modules.file.service;

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

    public void recordUploadedFile(Long tenantId, String objectKey, String fileName, String fileExt,
                                   String contentType, Long fileSize, String bizType) {
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        if (mapper != null) {
            FileObjectEntity entity = new FileObjectEntity();
            entity.setTenantId(tenantId != null ? tenantId : 1L);
            entity.setBucketId(1L);
            entity.setObjectKey(objectKey);
            entity.setFileName(fileName);
            entity.setFileExt(fileExt);
            entity.setContentType(contentType);
            entity.setFileSize(fileSize);
            entity.setStoragePath(objectKey);
            entity.setVisibility("PUBLIC");
            entity.setBizType(bizType);
            entity.setVersionNo(1);
            entity.setStatus("ACTIVE");
            mapper.insert(entity);
        }
    }
}
