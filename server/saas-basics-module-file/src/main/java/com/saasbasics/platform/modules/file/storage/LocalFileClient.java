package com.saasbasics.platform.modules.file.storage;

import com.saasbasics.platform.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件存储客户端实现。
 * 严格约束写入在配置目录范围内，防止路径穿越。
 */
public class LocalFileClient implements FileClient {

    private static final Logger log = LoggerFactory.getLogger(LocalFileClient.class);

    private final Path normalizedBasePath;
    private final String publicBaseUrl;

    public LocalFileClient(String baseDir, String publicBaseUrl) {
        this.normalizedBasePath = resolveWritableBaseDir(baseDir);
        this.publicBaseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl.replaceAll("/+$", "")
                : "/api/file/raw-download";
    }

    public Path getBasePath() {
        return normalizedBasePath;
    }

    private static Path resolveWritableBaseDir(String requestedDir) {
        if (requestedDir == null || requestedDir.isBlank()) {
            throw new BizException("FILE_STORAGE_SOURCE_NOT_CONFIGURED", "本地存储源未配置有效根目录路径");
        }
        String candidate = requestedDir.trim();
        Path resolved = Paths.get(candidate).toAbsolutePath().normalize();
        // Avoid resolving relative paths against '/' when launched as a system daemon
        if (candidate.startsWith(".") && resolved.startsWith("/data") && !Files.isWritable(Paths.get("/"))) {
            resolved = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "saas-basics-storage")
                    .toAbsolutePath().normalize();
        }
        try {
            Files.createDirectories(resolved);
            if (Files.isWritable(resolved)) {
                return resolved;
            }
        } catch (IOException ex) {
            throw new BizException("FILE_STORAGE_PATH_UNWRITABLE",
                    "无法创建或写入存储目录 [" + resolved + "]: " + ex.getMessage());
        }
        throw new BizException("FILE_STORAGE_PATH_UNWRITABLE",
                "配置的存储目录无写入权限: " + resolved);
    }

    private Path resolveSafeTargetPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BizException("FILE_INVALID_PATH", "文件路径不能为空");
        }
        String cleanRelative = relativePath.replace('\\', '/').replaceAll("^/+", "");
        Path target = normalizedBasePath.resolve(cleanRelative).normalize();
        if (!target.startsWith(normalizedBasePath)) {
            throw new BizException("FILE_INVALID_PATH", "禁止越权访问存储目录外的文件路径");
        }
        return target;
    }

    @Override
    public String upload(byte[] content, String path, String contentType) {
        try {
            Path targetPath = resolveSafeTargetPath(path);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.write(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("File uploaded locally to: {}", targetPath);
            return getPublicUrl(path);
        } catch (BizException ex) {
            throw ex;
        } catch (IOException e) {
            throw new BizException("FILE_UPLOAD_FAILED", "本地落盘保存失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String path) {
        try {
            Path targetPath = resolveSafeTargetPath(path);
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                throw new BizException("FILE_NOT_FOUND", "文件不存在: " + path);
            }
            return Files.readAllBytes(targetPath);
        } catch (BizException ex) {
            throw ex;
        } catch (IOException e) {
            throw new BizException("FILE_READ_FAILED", "读取本地文件失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path targetPath = resolveSafeTargetPath(path);
            Files.deleteIfExists(targetPath);
            log.info("File deleted locally: {}", path);
        } catch (IOException e) {
            log.warn("Failed to delete local file: {}", e.getMessage());
        }
    }

    @Override
    public String getPublicUrl(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        return publicBaseUrl + "/" + cleanPath;
    }
}
