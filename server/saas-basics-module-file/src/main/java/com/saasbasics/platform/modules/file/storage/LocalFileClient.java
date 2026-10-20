package com.saasbasics.platform.modules.file.storage;

import com.saasbasics.platform.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 本地文件存储客户端实现。
 * 适用于无外部 OSS 依赖的纯单机/私有化交付部署。
 */
public class LocalFileClient implements FileClient {

    private static final Logger log = LoggerFactory.getLogger(LocalFileClient.class);

    private final String baseDir;
    private final String publicBaseUrl;

    public LocalFileClient(String baseDir, String publicBaseUrl) {
        this.baseDir = (baseDir != null && !baseDir.isBlank()) ? baseDir : "./data/storage";
        this.publicBaseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl.replaceAll("/+$", "")
                : "/api/file/raw-download";

        // Ensure directory exists
        File dir = new File(this.baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public String upload(byte[] content, String path, String contentType) {
        try {
            Path targetPath = Paths.get(baseDir, path);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("File uploaded locally to: {}", targetPath.toAbsolutePath());
            return getPublicUrl(path);
        } catch (IOException e) {
            throw new BizException("FILE_UPLOAD_FAILED", "Failed to save file to local disk: " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String path) {
        try {
            Path targetPath = Paths.get(baseDir, path);
            if (!Files.exists(targetPath)) {
                throw new BizException("FILE_NOT_FOUND", "File does not exist: " + path);
            }
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            throw new BizException("FILE_READ_FAILED", "Failed to read file from disk: " + e.getMessage());
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path targetPath = Paths.get(baseDir, path);
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
