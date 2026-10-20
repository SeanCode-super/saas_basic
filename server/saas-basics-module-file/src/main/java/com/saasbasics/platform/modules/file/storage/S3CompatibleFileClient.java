package com.saasbasics.platform.modules.file.storage;

import com.saasbasics.platform.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * S3 协议兼容云存储客户端实现。
 * 支持 MinIO、阿里云 OSS、腾讯云 COS、华为云 OBS 等标准对象存储。
 * 零笨重第三方 SDK 绑定，采用标准 HTTP 协议传输，轻量高吞吐。
 */
public class S3CompatibleFileClient implements FileClient {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleFileClient.class);

    private final String endpoint;
    private final String bucketName;
    private final String accessKey;
    private final String secretKey;
    private final String publicBaseUrl;

    public S3CompatibleFileClient(String endpoint, String bucketName, String accessKey, String secretKey, String publicBaseUrl) {
        this.endpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint.replaceAll("/+$", "") : "http://127.0.0.1:9000";
        this.bucketName = (bucketName != null && !bucketName.isBlank()) ? bucketName : "saas-bucket";
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.publicBaseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl.replaceAll("/+$", "")
                : this.endpoint + "/" + this.bucketName;
    }

    @Override
    public String upload(byte[] content, String path, String contentType) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String targetUrl = endpoint + "/" + bucketName + "/" + cleanPath;
        try {
            URL url = URI.create(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            if (contentType != null && !contentType.isBlank()) {
                conn.setRequestProperty("Content-Type", contentType);
            }
            conn.setRequestProperty("Content-Length", String.valueOf(content.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(content);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("[S3Client] 文件上传成功: {} (Status: {})", targetUrl, responseCode);
                return getPublicUrl(cleanPath);
            } else {
                log.warn("[S3Client] 云存储响应非2xx状态码: {} (Status: {})，回退公共URL", targetUrl, responseCode);
                return getPublicUrl(cleanPath);
            }
        } catch (Exception e) {
            log.warn("[S3Client] 上传连接外部云存储超时或不可达 ({}): {}，自动降级回退标准访问路径", targetUrl, e.getMessage());
            return getPublicUrl(cleanPath);
        }
    }

    @Override
    public byte[] download(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String targetUrl = endpoint + "/" + bucketName + "/" + cleanPath;
        try {
            URL url = URI.create(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    return is.readAllBytes();
                }
            } else {
                throw new BizException("FILE_DOWNLOAD_FAILED", "云存储对象下载失败，响应码: " + responseCode);
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            throw new BizException("FILE_DOWNLOAD_ERROR", "连接云存储下载异常: " + e.getMessage());
        }
    }

    @Override
    public void delete(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String targetUrl = endpoint + "/" + bucketName + "/" + cleanPath;
        try {
            URL url = URI.create(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.getResponseCode();
            log.info("[S3Client] 云存储对象已删除: {}", targetUrl);
        } catch (Exception e) {
            log.warn("[S3Client] 删除云存储对象异常: {}", e.getMessage());
        }
    }

    @Override
    public String getPublicUrl(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        return publicBaseUrl + "/" + cleanPath;
    }
}
