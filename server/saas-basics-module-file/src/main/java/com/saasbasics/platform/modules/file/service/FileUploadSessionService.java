package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCompleteRequest;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadSessionEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import com.saasbasics.platform.modules.file.mapper.FileUploadSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FileUploadSessionService {

    private final ObjectProvider<FileUploadSessionMapper> sessionMapperProvider;
    private final ObjectProvider<FileObjectMapper> fileObjectMapperProvider;
    private final FileAccessLogService fileAccessLogService;

    public FileUploadSessionService(ObjectProvider<FileUploadSessionMapper> sessionMapperProvider,
                                    ObjectProvider<FileObjectMapper> fileObjectMapperProvider,
                                    FileAccessLogService fileAccessLogService) {
        this.sessionMapperProvider = sessionMapperProvider;
        this.fileObjectMapperProvider = fileObjectMapperProvider;
        this.fileAccessLogService = fileAccessLogService;
    }

    public List<FileUploadSessionResponse> listSessions() {
        FileUploadSessionMapper mapper = sessionMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectSessionList();
    }

    public FileUploadSessionResponse createSession(FileUploadSessionCreateRequest request) {
        FileUploadSessionMapper mapper = requiredMapper();
        FileUploadSessionEntity entity = new FileUploadSessionEntity();
        entity.setTenantId(request.tenantId());
        entity.setBucketId(request.bucketId());
        entity.setStorageId(request.storageId());
        entity.setSessionCode("UP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        entity.setObjectKey(request.objectKey());
        entity.setFileName(request.fileName());
        entity.setFileSize(request.fileSize());
        entity.setUploadMode(request.uploadMode());
        entity.setPartCount(request.partCount());
        entity.setOwnerUserId(request.ownerUserId());
        entity.setStatus("INIT");
        entity.setExpireAt(LocalDateTime.now().plusHours(2));
        mapper.insert(entity);
        return mapper.selectSessionList().stream()
                .filter(item -> item.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("FILE_UPLOAD_SESSION_NOT_FOUND", "Upload session not found"));
    }

    public FileObjectResponse completeSession(Long sessionId, FileUploadSessionCompleteRequest request) {
        FileUploadSessionMapper sessionMapper = requiredMapper();
        FileUploadSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || session.getDeleted() != null && session.getDeleted() == 1) {
            throw new BizException("FILE_UPLOAD_SESSION_NOT_FOUND", "Upload session not found");
        }

        FileObjectMapper objectMapper = requiredFileObjectMapper();
        FileObjectEntity object = new FileObjectEntity();
        object.setTenantId(request.tenantId());
        object.setBucketId(session.getBucketId());
        object.setObjectKey(session.getObjectKey());
        object.setFileName(session.getFileName());
        object.setFileExt(resolveFileExt(session.getFileName()));
        object.setContentType(request.contentType());
        object.setFileSize(session.getFileSize());
        object.setStoragePath(session.getObjectKey());
        object.setVisibility(request.visibility().toUpperCase(Locale.ROOT));
        object.setBizType(request.bizType());
        object.setVersionNo(1);
        object.setStatus("ACTIVE");
        object.setRemark(request.remark());
        objectMapper.insert(object);

        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        session.setRemark(request.remark());
        sessionMapper.updateById(session);

        fileAccessLogService.createLog(new FileAccessLogCreateRequest(
                request.tenantId(),
                object.getId(),
                "UPLOAD",
                session.getOwnerUserId(),
                "127.0.0.1",
                true,
                "上传会话完成并生成文件对象"
        ));

        return objectMapper.selectObjectList().stream()
                .filter(item -> item.id().equals(object.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("FILE_OBJECT_NOT_FOUND", "File object not found"));
    }

    private FileUploadSessionMapper requiredMapper() {
        FileUploadSessionMapper mapper = sessionMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }

    private FileObjectMapper requiredFileObjectMapper() {
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BizException("DB_PROFILE_REQUIRED", "Write operations require the db profile and MySQL connection");
        }
        return mapper;
    }

    private String resolveFileExt(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
