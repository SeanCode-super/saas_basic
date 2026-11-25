package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadSessionEntity;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
import com.saasbasics.platform.modules.file.storage.FileClient;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PlatformFileStorageGatewayImpl implements PlatformFileStorageGateway {

    private final FileStorageService fileStorageService;
    private final FileUploadRouteService fileUploadRouteService;
    private final FileClientFactory fileClientFactory;
    private final FileObjectService fileObjectService;
    private final FileAccessLogService fileAccessLogService;
    private final FileUploadAccounting accounting;

    public PlatformFileStorageGatewayImpl(FileStorageService fileStorageService,
                                          FileUploadRouteService fileUploadRouteService,
                                          FileClientFactory fileClientFactory,
                                          FileObjectService fileObjectService,
                                          FileAccessLogService fileAccessLogService,
                                          FileUploadAccounting accounting) {
        this.fileStorageService = fileStorageService;
        this.fileUploadRouteService = fileUploadRouteService;
        this.fileClientFactory = fileClientFactory;
        this.fileObjectService = fileObjectService;
        this.fileAccessLogService = fileAccessLogService;
        this.accounting = accounting;
    }

    @Override
    public StorageAvailability checkAvailability(Long tenantId, String bizType) {
        Long resolvedTenant = resolveTenantId(tenantId);
        FileUploadRouteEntity route = fileUploadRouteService.findMatchingRoute(resolvedTenant, bizType);
        FileStorageEntity storage = resolveTargetStorage(resolvedTenant, route);
        if (storage == null) {
            String msg = route != null && route.getStorageId() != null
                    ? "业务类型 [" + bizType + "] 指定的存储源未启用或已失效，请前往「文件中心 -> 存储策略」配置或重新启用存储源"
                    : "当前尚未配置或启用任何文件存储源，请先前往「文件中心 -> 存储策略」配置存储源与默认上传目录后再试";
            return new StorageAvailability(
                    false,
                    "FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    msg,
                    null, null, null, null, null, null, null, null, null
            );
        }

        String defaultFolder = FilePathPolicyHelper.sanitizeFolder(
                storage.getDefaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER);
        String targetDir = route != null
                ? FilePathPolicyHelper.sanitizeFolder(route.getTargetDir(), defaultFolder)
                : defaultFolder;
        String uploadMode = route != null && route.getUploadMode() != null && !route.getUploadMode().isBlank()
                ? route.getUploadMode()
                : (storage.getUploadMode() != null ? storage.getUploadMode() : FilePathPolicyHelper.DEFAULT_UPLOAD_MODE);
        String pattern = route != null && route.getPathPattern() != null && !route.getPathPattern().isBlank()
                ? route.getPathPattern()
                : (storage.getPathPattern() != null ? storage.getPathPattern() : FilePathPolicyHelper.DEFAULT_PATH_PATTERN);

        String msg = "已连接存储源「" + storage.getStorageName() + "」· 目标目录: " + targetDir;
        return new StorageAvailability(
                true,
                "OK",
                msg,
                storage.getId(),
                storage.getStorageCode(),
                storage.getStorageName(),
                storage.getStorageType(),
                uploadMode,
                storage.getBasePath(),
                defaultFolder,
                targetDir,
                pattern
        );
    }

    @Override
    public StoredUploadResult uploadBytes(Long tenantId,
                                          String bizType,
                                          String originalFilename,
                                          String contentType,
                                          byte[] content) {
        return uploadBytes(tenantId, bizType, originalFilename, contentType, content, null);
    }

    @Override
    public StoredUploadResult uploadBytes(Long tenantId, String bizType, String originalFilename,
                                          String contentType, byte[] content, String requestKey) {
        if (content == null || content.length == 0) {
            throw new BizException("FILE_EMPTY", "上传文件内容不能为空");
        }
        Long resolvedTenant = resolveTenantId(tenantId);
        String normalizedBizType = (bizType != null && !bizType.isBlank())
                ? bizType.trim().toUpperCase(Locale.ROOT)
                : "COMMON";

        FileUploadRouteEntity route = fileUploadRouteService.findMatchingRoute(resolvedTenant, normalizedBizType);
        FileStorageEntity storage = resolveTargetStorage(resolvedTenant, route);
        if (storage == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前尚未配置可用的文件存储源，请先前往「文件中心 -> 存储策略」配置并启用存储源与默认上传目录");
        }

        String safeFilename = FilePathPolicyHelper.sanitizeFilename(originalFilename);
        String ext = FilePathPolicyHelper.resolveFileExtension(safeFilename);

        Integer maxFileMb = (route != null && route.getMaxFileMb() != null)
                ? route.getMaxFileMb()
                : storage.getMaxFileMb();
        String allowedExts = (route != null && route.getAllowedExts() != null && !route.getAllowedExts().isBlank())
                ? route.getAllowedExts()
                : storage.getAllowedExts();
        FilePathPolicyHelper.validateUploadConstraints(content.length, ext, maxFileMb, allowedExts);

        String defaultFolder = FilePathPolicyHelper.sanitizeFolder(
                storage.getDefaultFolder(), FilePathPolicyHelper.DEFAULT_FOLDER);
        String targetDir = route != null
                ? FilePathPolicyHelper.sanitizeFolder(route.getTargetDir(), defaultFolder)
                : defaultFolder;
        String pattern = route != null && route.getPathPattern() != null && !route.getPathPattern().isBlank()
                ? route.getPathPattern()
                : storage.getPathPattern();
        String uploadMode = route != null && route.getUploadMode() != null && !route.getUploadMode().isBlank()
                ? route.getUploadMode()
                : (storage.getUploadMode() != null ? storage.getUploadMode() : FilePathPolicyHelper.DEFAULT_UPLOAD_MODE);

        String objectKey = FilePathPolicyHelper.resolveObjectKey(pattern, targetDir, normalizedBizType, resolvedTenant, safeFilename);
        FileClient client = fileClientFactory.getClient(storage);
        FileUploadSessionEntity candidate = new FileUploadSessionEntity();
        candidate.setTenantId(resolvedTenant);
        candidate.setBucketId(1L);
        candidate.setStorageId(storage.getId());
        candidate.setObjectKey(objectKey);
        candidate.setFileName(safeFilename);
        candidate.setFileSize((long) content.length);
        candidate.setPartCount(1);
        candidate.setOwnerUserId(AuthContext.getUserId() == null ? 0L : AuthContext.getUserId());
        candidate.setUploadMode(uploadMode);
        String fingerprint = requestKey == null ? null : FileUploadAccounting.fingerprint(
                normalizedBizType, safeFilename, contentType, FileUploadAccounting.contentHash(content));
        FileUploadSessionEntity session = accounting.reserve(candidate, requestKey, fingerprint, true);
        if ("COMPLETED".equals(session.getStatus())) {
            FileObjectEntity existing = accounting.completedObject(session);
            FileStorageEntity originalStorage = fileStorageService.findActiveStorageById(resolvedTenant, existing.getStorageId());
            if (originalStorage == null) throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED", "原上传存储源不可用");
            return storedResult(existing, originalStorage, fileClientFactory.getClient(originalStorage));
        }

        String physicalStoragePath = buildStoragePath(storage, objectKey);
        String visibility = "USER_AVATAR".equals(normalizedBizType) || "PORTAL_ASSET".equals(normalizedBizType)
                ? "PUBLIC"
                : "PRIVATE";
        String effectiveContentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : "application/octet-stream";

        FileObjectEntity saved;
        try {
            client.upload(content, objectKey, effectiveContentType);
            saved = accounting.finish(resolvedTenant, session.getId(), () -> fileObjectService.recordUploadedFile(
                resolvedTenant,
                storage.getId(),
                objectKey,
                safeFilename,
                ext,
                effectiveContentType,
                (long) content.length,
                normalizedBizType,
                uploadMode,
                targetDir,
                physicalStoragePath,
                visibility
            ));
        } catch (RuntimeException failure) {
            accounting.discard(resolvedTenant, session.getId(), false, ignored -> client.delete(objectKey));
            throw failure;
        }

        try {
            if (saved.getId() != null) {
                AuthPrincipal principal = AuthContext.get();
                Long userId = principal != null && principal.userId() != null ? principal.userId() : 1L;
                fileAccessLogService.createLog(new FileAccessLogCreateRequest(
                        resolvedTenant,
                        saved.getId(),
                        "UPLOAD",
                        userId,
                        "127.0.0.1",
                        true,
                        "模式: " + uploadMode + " · 目录: " + targetDir
                ));
            }
        } catch (Exception ignored) {
            // Audit log failures should not roll back a successful storage upload
        }

        return storedResult(saved, storage, client);
    }

    private StoredUploadResult storedResult(FileObjectEntity file, FileStorageEntity storage, FileClient client) {
        String url = "LOCAL".equalsIgnoreCase(storage.getStorageType())
                ? "/api/file/raw-download/" + file.getObjectKey() : client.getPublicUrl(file.getObjectKey());
        return new StoredUploadResult(file.getId(), storage.getId(), storage.getStorageCode(), storage.getStorageType(),
                file.getUploadMode(), file.getTargetDir(), file.getObjectKey(), url, file.getFileName(),
                file.getFileSize(), file.getContentType());
    }

    @Override
    public byte[] downloadObject(Long tenantId, String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new BizException("FILE_INVALID_PATH", "非法的对象路径");
        }
        String cleanKey = objectKey.replace('\\', '/').replaceAll("^/+", "");
        Long resolvedTenant = resolveTenantId(tenantId);
        FilePathPolicyHelper.assertTenantKeyAccess(resolvedTenant, cleanKey);
        FileObjectEntity obj = fileObjectService.findByObjectKey(resolvedTenant, cleanKey);
        if (obj == null && fileObjectService.isOwnedByAnotherTenant(resolvedTenant, cleanKey)) {
            throw new BizException("FILE_CROSS_TENANT_FORBIDDEN", "禁止跨租户下载其他租户文件");
        }
        FileStorageEntity storage = null;
        if (obj != null && obj.getStorageId() != null) {
            storage = fileStorageService.findActiveStorageById(resolvedTenant, obj.getStorageId());
        } else {
            storage = fileStorageService.findActiveDefaultStorage(resolvedTenant);
        }
        if (storage == null) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED",
                    "当前没有可用的文件存储源，无法读取目标文件");
        }
        return fileClientFactory.getClient(storage).download(cleanKey);
    }

    private FileStorageEntity resolveTargetStorage(Long tenantId, FileUploadRouteEntity route) {
        if (route != null && route.getStorageId() != null) {
            return fileStorageService.findActiveStorageById(tenantId, route.getStorageId());
        }
        return fileStorageService.findActiveDefaultStorage(tenantId);
    }

    private static String buildStoragePath(FileStorageEntity storage, String objectKey) {
        if ("LOCAL".equalsIgnoreCase(storage.getStorageType())) {
            String base = storage.getBasePath() != null && !storage.getBasePath().isBlank()
                    ? storage.getBasePath().replaceAll("/+$", "")
                    : "./data/storage";
            return base + "/" + objectKey;
        }
        String bucket = storage.getBucketDefault() != null ? storage.getBucketDefault() : "default";
        return "/" + bucket + "/" + objectKey;
    }

    private static Long resolveTenantId(Long candidate) {
        if (candidate != null && candidate > 0) {
            return candidate;
        }
        AuthPrincipal principal = AuthContext.get();
        return principal != null && principal.tenantId() != null ? principal.tenantId() : 1L;
    }
}
