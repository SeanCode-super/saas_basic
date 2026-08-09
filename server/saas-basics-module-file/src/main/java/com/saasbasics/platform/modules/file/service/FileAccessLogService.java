package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileAccessLogResponse;
import com.saasbasics.platform.modules.file.entity.FileAccessLogEntity;
import com.saasbasics.platform.modules.file.mapper.FileAccessLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FileAccessLogService {

    private final ObjectProvider<FileAccessLogMapper> mapperProvider;

    public FileAccessLogService(ObjectProvider<FileAccessLogMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public List<FileAccessLogResponse> listLogs() {
        FileAccessLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectLogList();
    }

    public FileAccessLogResponse createLog(FileAccessLogCreateRequest request) {
        FileAccessLogMapper mapper = requiredMapper();
        FileAccessLogEntity entity = new FileAccessLogEntity();
        entity.setTenantId(request.tenantId());
        entity.setFileId(request.fileId());
        entity.setAccessType(request.accessType());
        entity.setOperatorUserId(request.operatorUserId());
        entity.setOperatorIp(request.operatorIp());
        entity.setSuccess(request.success());
        entity.setOccurredAt(LocalDateTime.now());
        entity.setRemark(request.remark());
        mapper.insert(entity);
        return mapper.selectLogList().stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("FILE_ACCESS_LOG_NOT_FOUND", "File access log not found"));
    }

    private FileAccessLogMapper requiredMapper() {
        FileAccessLogMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }
}
