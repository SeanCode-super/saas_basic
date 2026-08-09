package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
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
}
