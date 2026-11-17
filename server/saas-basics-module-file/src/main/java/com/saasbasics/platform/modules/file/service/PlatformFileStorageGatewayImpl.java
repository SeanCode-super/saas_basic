package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.file.dto.FileAccessLogCreateRequest;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
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

    public PlatformFileStorageGatewayImpl(FileStorageService fileStorageService,
                                          FileUploadRouteService fileUploadRouteService,
                                          FileClientFactory fileClientFactory,
                                          FileObjectService fileObjectService,
                                          FileAccessLogService fileAccessLogService) {
        this.fileStorageService = fileStorageService;
        this.fileUploadRouteService = fileUploadRouteService;
        this.fileClientFactory = fileClientFactory;
        this.fileObjectService = fileObjectService;
        this.fileAccessLogService = fileAccessLogService;
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
        String uploadedUrl = client.upload(content, objectKey, contentType);
        String publicUrl = "/api/file/raw-download/" + objectKey;
        if (!"LOCAL".equalsIgnoreCase(storage.getStorageType()) && uploadedUrl != null && !uploadedUrl.isBlank()) {
            publicUrl = uploadedUrl;
        }

        String physicalStoragePath = buildStoragePath(storage, objectKey);
        String visibility = "USER_AVATAR".equals(normalizedBizType) || "PORTAL_ASSET".equals(normalizedBizType)
                ? "PUBLIC"
                : "PRIVATE";
        String effectiveContentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : "application/octet-stream";

        FileObjectEntity saved = fileObjectService.recordUploadedFile(
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
        );

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

        return new StoredUploadResult(
                saved.getId(),
                storage.getId(),
                storage.getStorageCode(),
                storage.getStorageType(),
                uploadMode,
                targetDir,
                objectKey,
                publicUrl,
                safeFilename,
                (long) content.length,
                effectiveContentType
        );
    }

    @Override
    public byte[] downloadObject(Long tenantId, String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new BizException("FILE_INVALID_PATH", "非法的对象路径");
        }
        String cleanKey = objectKey.replace('\\', '/').replaceAll("^/+", "");
        Long resolvedTenant = resolveTenantId(tenantId);
        FileObjectEntity obj = fileObjectService.findByObjectKey(resolvedTenant, cleanKey);
        FileStorageEntity storage = null;
        if (obj != null && obj.getStorageId() != null) {
            storage = fileStorageService.findActiveStorageById(resolvedTenant, obj.getStorageId());
        }
        if (storage == null) {
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
