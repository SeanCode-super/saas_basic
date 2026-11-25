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
import com.saasbasics.platform.modules.file.storage.FileClient;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import com.saasbasics.platform.common.tenant.TenantAccessContext;
import com.saasbasics.platform.common.tenant.TenantAccessContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FileUploadSessionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUploadSessionService.class);

    private final ObjectProvider<FileUploadSessionMapper> sessionMapperProvider;
    private final ObjectProvider<FileObjectMapper> fileObjectMapperProvider;
    private final FileAccessLogService fileAccessLogService;
    private final FileStorageService fileStorageService;
    private final FileUploadRouteService fileUploadRouteService;
    private final ObjectProvider<FileClientFactory> fileClientFactoryProvider;

    private final FileUploadAccounting accounting;

    @Autowired
    public FileUploadSessionService(ObjectProvider<FileUploadSessionMapper> sessionMapperProvider,
                                    ObjectProvider<FileObjectMapper> fileObjectMapperProvider,
                                    FileAccessLogService fileAccessLogService,
                                    FileStorageService fileStorageService,
                                    FileUploadRouteService fileUploadRouteService,
                                    ObjectProvider<FileClientFactory> fileClientFactoryProvider,
                                    FileUploadAccounting accounting) {
        this.accounting = accounting;
        this.sessionMapperProvider = sessionMapperProvider;
        this.fileObjectMapperProvider = fileObjectMapperProvider;
        this.fileAccessLogService = fileAccessLogService;
        this.fileStorageService = fileStorageService;
        this.fileUploadRouteService = fileUploadRouteService;
        this.fileClientFactoryProvider = fileClientFactoryProvider;
    }

    public List<FileUploadSessionResponse> listSessions() {
        FileUploadSessionMapper mapper = sessionMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        return mapper.selectSessionList();
    }

    public FileUploadSessionResponse createSession(FileUploadSessionCreateRequest request, String requestKey) {
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
            FilePathPolicyHelper.assertTenantKeyAccess(request.tenantId(), resolvedObjectKey);
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
        entity.setObjectKey(resolvedObjectKey);
        entity.setFileName(safeFilename);
        entity.setFileSize(request.fileSize());
        entity.setUploadMode(uploadMode);
        entity.setPartCount(request.partCount());
        entity.setOwnerUserId(request.ownerUserId());
        String fingerprint = requestKey == null ? null : FileUploadAccounting.fingerprint(
                String.valueOf(request.bucketId()), String.valueOf(request.storageId()), request.objectKey(),
                safeFilename, String.valueOf(request.fileSize()), uploadMode, String.valueOf(request.partCount()), bizType);
        FileUploadSessionEntity session = accounting.reserve(entity, requestKey, fingerprint, false);
        return new FileUploadSessionResponse(session.getId(), session.getTenantId(), session.getBucketId(),
                session.getStorageId(), session.getSessionCode(), session.getObjectKey(), session.getFileName(),
                session.getFileSize(), session.getUploadMode(), session.getPartCount(), session.getOwnerUserId(),
                session.getStatus(), session.getExpireAt(), session.getCompletedAt());
    }

    public FileObjectResponse completeSession(Long sessionId, FileUploadSessionCompleteRequest request) {
        FileUploadSessionEntity session = accounting.ownedSession(sessionId, request.tenantId());
        if ("COMPLETED".equals(session.getStatus())) return response(accounting.completedObject(session));
        if (!"INIT".equals(session.getStatus()) || !FileUploadAccounting.active(session)) {
            throw new BizException("FILE_UPLOAD_SESSION_EXPIRED", "上传会话已失效");
        }
        FileClient client = client(session);
        if (client.size(session.getObjectKey()) != session.getFileSize()) {
            // A still-valid signed URL may be retried. Keep the reservation until cancellation or expiry.
            throw new BizException("FILE_UPLOAD_SIZE_MISMATCH", "对象实际大小与上传会话不一致");
        }
        FileObjectEntity file = accounting.finish(request.tenantId(), sessionId, () -> registerObject(session, request));
        return response(file);
    }

    private FileObjectResponse response(FileObjectEntity file) {
        return new FileObjectResponse(file.getId(), file.getTenantId(), file.getBucketId(), file.getStorageId(),
                file.getObjectKey(), file.getFileName(), file.getFileExt(), file.getContentType(), file.getFileSize(),
                file.getVisibility(), file.getBizType(), file.getVersionNo(), file.getStatus(), file.getStoragePath(),
                file.getUploadMode(), file.getTargetDir(), "/api/file/raw-download/" + file.getObjectKey(), file.getRemark());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanExpiredReservations() {
        List<FileUploadSessionMapper.ExpiredSession> expired;
        try (var scope = TenantAccessContextHolder.openSystemBypass(
                TenantAccessContext.BypassOperation.SYSTEM_TASK_OR_ANNOTATION_IGNORE, "Find expired upload reservations")) {
            expired = requiredMapper().listExpiredSessions();
        }
        for (var item : expired) {
            try (var scope = TenantAccessContextHolder.openTenant(item.tenantId(), item.tenantCode(), 0L)) {
                accounting.discard(item.tenantId(), item.id(), true,
                        session -> client(session).delete(session.getObjectKey()));
            } catch (RuntimeException ex) {
                log.warn("Could not reconcile upload session {}", item.id(), ex);
            }
        }
    }

    private FileClient client(FileUploadSessionEntity session) {
        FileStorageEntity storage = fileStorageService.findActiveStorageById(session.getTenantId(), session.getStorageId());
        if (storage == null) throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED", "上传会话的存储源不可用");
        return fileClientFactoryProvider.getObject().getClient(storage);
    }

    private FileObjectEntity registerObject(FileUploadSessionEntity session, FileUploadSessionCompleteRequest request) {
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

        fileAccessLogService.createLog(new FileAccessLogCreateRequest(
                request.tenantId(),
                object.getId(),
                "UPLOAD",
                session.getOwnerUserId(),
                "127.0.0.1",
                true,
                request.remark()
        ));

        return object;
    }

    public Map<String, Object> generatePresignedUrl(Long tenantId, Long storageId, String objectKey, String method, int expireSeconds) {
        FileStorageEntity activeStorage = resolveStorage(tenantId, storageId, null);
        if (activeStorage == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置或启用可用的文件存储源，无法签发预签名直传地址");
        }
        String safeKey = FilePathPolicyHelper.sanitizeFolder(objectKey, null);
        if (safeKey == null || safeKey.isBlank()) {
            throw new BizException("FILE_INVALID_PATH", "对象路径 objectKey 不能为空");
        }
        FilePathPolicyHelper.assertTenantKeyAccess(tenantId, safeKey);
        FileClientFactory factory = fileClientFactoryProvider != null ? fileClientFactoryProvider.getIfAvailable() : null;
        if (factory == null) {
            throw new BizException("FILE_CLIENT_FACTORY_UNAVAILABLE", "存储客户端工厂未就绪");
        }
        FileClient client = factory.getClient(activeStorage);
        String normalizedMethod = "GET".equalsIgnoreCase(method) ? "GET" : "PUT";
        int safeExpires = Math.min(Math.max(expireSeconds, 60), 86400);
        String url;
        if ("GET".equals(normalizedMethod)) {
            url = client.generatePresignedDownloadUrl(safeKey, safeExpires);
        } else {
            FileUploadSessionEntity reservation = accounting.pendingPut(tenantId, activeStorage.getId(), safeKey);
            long remaining = java.time.Duration.between(LocalDateTime.now(), reservation.getExpireAt()).toSeconds();
            if (remaining < 60) throw new BizException("FILE_UPLOAD_SESSION_EXPIRED", "上传会话即将过期");
            safeExpires = (int) Math.min(safeExpires, remaining);
            url = client.generatePresignedUploadUrl(safeKey, safeExpires, reservation.getFileSize());
        }
        return Map.of(
                "storageId", activeStorage.getId(),
                "storageType", activeStorage.getStorageType(),
                "objectKey", safeKey,
                "method", normalizedMethod,
                "expireSeconds", safeExpires,
                "url", url
        );
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
