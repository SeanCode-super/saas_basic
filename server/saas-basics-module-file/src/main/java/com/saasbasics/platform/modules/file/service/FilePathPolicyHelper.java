package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.common.exception.BizException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shared validation and template expansion helper for configurable file upload directories and object keys.
 */
public final class FilePathPolicyHelper {

    public static final String DEFAULT_FOLDER = "uploads";
    public static final String DEFAULT_PATH_PATTERN = "t_{tenantId}/{folder}/{yyyy}/{MM}/{uuid}_{filename}";
    public static final String DEFAULT_UPLOAD_MODE = "DIRECT_SERVER";
    public static final Set<String> VALID_UPLOAD_MODES = Set.of(
            "DIRECT_SERVER",
            "LOCAL_DISK",
            "PRESIGNED",
            "CHUNKED"
    );

    private static final Pattern UNSAFE_SEGMENT = Pattern.compile("[:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TENANT_PREFIX_PATTERN = Pattern.compile("^t_(\\d+)(?:/.*|$)");
    private static final Pattern EXPORT_PREFIX_PATTERN = Pattern.compile("^exports/t(\\d+)(?:/.*|$)");
    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MM = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DD = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private FilePathPolicyHelper() {
    }

    public static String normalizeUploadMode(String mode, String fallback) {
        if (mode == null || mode.isBlank()) {
            return fallback;
        }
        String upper = mode.trim().toUpperCase(Locale.ROOT);
        if (!VALID_UPLOAD_MODES.contains(upper)) {
            throw new BizException("FILE_INVALID_UPLOAD_MODE", "不支持的上传方式: " + mode);
        }
        return upper;
    }

    public static String sanitizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return null;
        }
        String normalized = basePath.trim().replace('\\', '/');
        if (normalized.contains("\0")) {
            throw new BizException("FILE_INVALID_PATH", "非法的存储根目录");
        }
        for (String seg : normalized.split("/")) {
            if ("..".equals(seg)) {
                throw new BizException("FILE_INVALID_PATH", "存储根目录不允许包含上级目录跳转 (..)");
            }
        }
        return normalized;
    }

    public static String sanitizeFolder(String rawFolder, String fallback) {
        if (rawFolder == null || rawFolder.isBlank()) {
            return fallback;
        }
        String cleaned = rawFolder.trim().replace('\\', '/');
        cleaned = cleaned.replaceAll("^/+", "").replaceAll("/+$", "");
        if (cleaned.isEmpty()) {
            return fallback;
        }
        String[] parts = cleaned.split("/");
        StringBuilder out = new StringBuilder();
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part) || UNSAFE_SEGMENT.matcher(part).find() || part.contains("\0")) {
                throw new BizException("FILE_INVALID_PATH", "非法的上传目录名称: " + rawFolder);
            }
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(part);
        }
        return out.isEmpty() ? fallback : out.toString();
    }

    public static String sanitizeFilename(String rawFilename) {
        String name = (rawFilename == null || rawFilename.isBlank()) ? "unnamed.bin" : rawFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = UNSAFE_SEGMENT.matcher(name).replaceAll("_").replace("..", "_").trim();
        if (name.isEmpty()) {
            return "unnamed.bin";
        }
        return name;
    }

    public static String resolveFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    public static String resolveObjectKey(String pattern,
                                          String folder,
                                          String bizType,
                                          Long tenantId,
                                          String safeFilename) {
        String tpl = (pattern != null && !pattern.isBlank()) ? pattern.trim() : DEFAULT_PATH_PATTERN;
        String resolvedFolder = sanitizeFolder(folder, DEFAULT_FOLDER);
        if (!tpl.contains("{folder}") && resolvedFolder != null && !resolvedFolder.isBlank()) {
            tpl = resolvedFolder + "/" + tpl;
        }
        LocalDateTime now = LocalDateTime.now();
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String safeBiz = sanitizeFolder(bizType != null ? bizType.toLowerCase(Locale.ROOT) : "common", "common");

        String key = tpl
                .replace("{folder}", resolvedFolder)
                .replace("{yyyy}", now.format(YYYY))
                .replace("{MM}", now.format(MM))
                .replace("{dd}", now.format(DD))
                .replace("{yyyyMMdd}", now.format(YYYYMMDD))
                .replace("{bizType}", safeBiz)
                .replace("{tenantId}", String.valueOf(tenantId != null ? tenantId : 1L))
                .replace("{uuid}", shortUuid)
                .replace("{filename}", safeFilename);

        key = key.replace('\\', '/').replaceAll("/{2,}", "/").replaceAll("^/+", "");
        if (key.isBlank() || key.contains("..")) {
            throw new BizException("FILE_INVALID_PATH", "生成的对象路径非法");
        }
        return key;
    }

    /**
     * 校验对象路径前缀是否越权指向了其他租户的隔离目录（t_{tenantId}/... 或 exports/t{tenantId}/...）。
     */
    public static void assertTenantKeyAccess(Long tenantId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        String cleanKey = objectKey.trim().replace('\\', '/').replaceAll("^/+", "");
        long currentTenantId = (tenantId != null && tenantId > 0L) ? tenantId : 1L;
        checkPrefixTenantMatch(TENANT_PREFIX_PATTERN.matcher(cleanKey), currentTenantId, "禁止跨租户访问其他租户空间的文件路径");
        checkPrefixTenantMatch(EXPORT_PREFIX_PATTERN.matcher(cleanKey), currentTenantId, "禁止跨租户访问其他租户空间的导出文件");
    }

    private static void checkPrefixTenantMatch(Matcher matcher, long currentTenantId, String errorMsg) {
        if (!matcher.matches()) {
            return;
        }
        try {
            long pathTenantId = Long.parseLong(matcher.group(1));
            if (pathTenantId != currentTenantId) {
                throw new BizException("FILE_CROSS_TENANT_FORBIDDEN", errorMsg);
            }
        } catch (NumberFormatException ex) {
            throw new BizException("FILE_INVALID_PATH", "非法的租户文件目录前缀");
        }
    }

    public static void validateUploadConstraints(long fileSize,
                                                 String ext,
                                                 Integer maxFileMb,
                                                 String allowedExts) {
        if (maxFileMb != null && maxFileMb > 0) {
            long maxBytes = maxFileMb * 1024L * 1024L;
            if (fileSize > maxBytes) {
                throw new BizException("FILE_SIZE_EXCEEDED",
                        "文件体积超过当前目录/存储源限制 (" + maxFileMb + " MB)");
            }
        }
        if (allowedExts != null && !allowedExts.isBlank()) {
            Set<String> allowed = Arrays.stream(allowedExts.split("[,;\\s]+"))
                    .map(s -> s.trim().replaceFirst("^\\.", "").toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (!allowed.isEmpty()) {
                String normalizedExt = ext != null ? ext.trim().toLowerCase(Locale.ROOT) : "";
                if (normalizedExt.isEmpty() || !allowed.contains(normalizedExt)) {
                    throw new BizException("FILE_TYPE_NOT_ALLOWED",
                            "当前目录仅允许上传 [" + String.join(", ", allowed) + "] 格式文件");
                }
            }
        }
    }
}
