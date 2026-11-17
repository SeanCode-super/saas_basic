package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileObjectResponse;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCompleteRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionCreateRequest;
import com.saasbasics.platform.modules.file.dto.FileUploadSessionResponse;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
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
    private final FileStorageService fileStorageService;
    private final FileUploadRouteService fileUploadRouteService;

    public FileUploadSessionService(ObjectProvider<FileUploadSessionMapper> sessionMapperProvider,
                                    ObjectProvider<FileObjectMapper> fileObjectMapperProvider,
                                    FileAccessLogService fileAccessLogService,
                                    FileStorageService fileStorageService,
                                    FileUploadRouteService fileUploadRouteService) {
        this.sessionMapperProvider = sessionMapperProvider;
        this.fileObjectMapperProvider = fileObjectMapperProvider;
        this.fileAccessLogService = fileAccessLogService;
        this.fileStorageService = fileStorageService;
        this.fileUploadRouteService = fileUploadRouteService;
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
        String bizType = request.bizType() != null && !request.bizType().isBlank()
                ? request.bizType().trim().toUpperCase(Locale.ROOT)
                : "COMMON";

        FileUploadRouteEntity route = fileUploadRouteService.findMatchingRoute(request.tenantId(), bizType);
        FileStorageEntity activeStorage = resolveStorage(request.tenantId(), request.storageId(), route);
        if (activeStorage == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置或启用可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源后再创建上传会话");
        }

        String safeFilename = FilePathPolicyHelper.sanitizeFilename(request.fileName());
        String ext = FilePathPolicyHelper.resolveFileExtension(safeFilename);
        Integer maxMb = (route != null && route.getMaxFileMb() != null)
                ? route.getMaxFileMb()
                : activeStorage.getMaxFileMb();
        String allowedExts = (route != null && route.getAllowedExts() != null && !route.getAllowedExts().isBlank())
                ? route.getAllowedExts()
                : activeStorage.getAllowedExts();
        FilePathPolicyHelper.validateUploadConstraints(request.fileSize(), ext, maxMb, allowedExts);

        String defaultFolder = FilePathPolicyHelper.sanitizeFolder(
                activeStorage.getDefaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER);
        String targetDir = route != null
                ? FilePathPolicyHelper.sanitizeFolder(route.getTargetDir(), defaultFolder)
                : defaultFolder;
        String pattern = route != null && route.getPathPattern() != null && !route.getPathPattern().isBlank()
                ? route.getPathPattern()
                : activeStorage.getPathPattern();

        String resolvedObjectKey;
        if (request.objectKey() != null && !request.objectKey().isBlank()) {
            resolvedObjectKey = FilePathPolicyHelper.sanitizeFolder(request.objectKey(), null);
        } else {
            resolvedObjectKey = FilePathPolicyHelper.resolveObjectKey(pattern, targetDir, bizType, request.tenantId(), safeFilename);
        }

        String uploadMode = request.uploadMode() != null && !request.uploadMode().isBlank()
                ? request.uploadMode().trim().toUpperCase(Locale.ROOT)
                : (route != null && route.getUploadMode() != null ? route.getUploadMode() : activeStorage.getUploadMode());

        FileUploadSessionEntity entity = new FileUploadSessionEntity();
        entity.setTenantId(request.tenantId());
        entity.setBucketId(request.bucketId() != null ? request.bucketId() : 1L);
        entity.setStorageId(activeStorage.getId());
        entity.setSessionCode("UP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        entity.setObjectKey(resolvedObjectKey);
        entity.setFileName(safeFilename);
        entity.setFileSize(request.fileSize());
        entity.setUploadMode(uploadMode);
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
        if (session == null || (session.getDeleted() != null && session.getDeleted() == 1)) {
            throw new BizException("FILE_UPLOAD_SESSION_NOT_FOUND", "Upload session not found");
        }

        FileObjectMapper objectMapper = requiredFileObjectMapper();
        FileObjectEntity object = new FileObjectEntity();
        object.setTenantId(request.tenantId());
        object.setBucketId(session.getBucketId());
        object.setStorageId(session.getStorageId());
        object.setObjectKey(session.getObjectKey());
        object.setFileName(session.getFileName());
        object.setFileExt(FilePathPolicyHelper.resolveFileExtension(session.getFileName()));
        object.setContentType(request.contentType());
        object.setFileSize(session.getFileSize());
        object.setStoragePath(session.getObjectKey());
        object.setUploadMode(session.getUploadMode());
        int lastSlash = session.getObjectKey() != null ? session.getObjectKey().lastIndexOf('/') : -1;
        object.setTargetDir(lastSlash > 0 ? session.getObjectKey().substring(0, lastSlash) : FilePathPolicyHelper.DEFAULT_FOLDER);
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
                request.remark()
        ));

        return objectMapper.selectObjectList().stream()
                .filter(item -> item.id().equals(object.getId()))
                .findFirst()
                .orElseThrow(() -> new BizException("FILE_OBJECT_NOT_FOUND", "File object not found"));
    }

    private FileStorageEntity resolveStorage(Long tenantId, Long requestedStorageId, FileUploadRouteEntity route) {
        if (requestedStorageId != null && requestedStorageId > 0) {
            FileStorageEntity explicit = fileStorageService.findActiveStorageById(tenantId, requestedStorageId);
            if (explicit != null) {
                return explicit;
            }
        }
        if (route != null && route.getStorageId() != null) {
            return fileStorageService.findActiveStorageById(tenantId, route.getStorageId());
        }
        return fileStorageService.findActiveDefaultStorage(tenantId);
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
}
