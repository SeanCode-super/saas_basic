package com.saasbasics.platform.modules.file.storage;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.security.SsrfProtectionValidator;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * S3 协议兼容云存储客户端实现（支持 AWS SigV4 签名与预签名直传）。
 * 支持 MinIO、阿里云 OSS S3 兼容端点、腾讯云 COS、华为云 OBS、Cloudflare R2 等标准对象存储。
 * 严格遵循失败关闭原则：远端上传异常时明确抛出错误，禁止静默返回伪造成功 URL。
 */
public class S3CompatibleFileClient implements FileClient {

    private static final Logger log = LoggerFactory.getLogger(S3CompatibleFileClient.class);

    private final String endpoint;
    private final String bucketName;
    private final String accessKey;
    private final String secretKey;
    private final String publicBaseUrl;
    private final String region;
    private final Environment environment;

    public S3CompatibleFileClient(String endpoint, String bucketName, String accessKey, String secretKey, String publicBaseUrl) {
        this(endpoint, bucketName, accessKey, secretKey, publicBaseUrl, "us-east-1", null);
    }

    public S3CompatibleFileClient(
            String endpoint,
            String bucketName,
            String accessKey,
            String secretKey,
            String publicBaseUrl,
            String region,
            Environment environment
    ) {
        this.endpoint = (endpoint != null && !endpoint.isBlank()) ? endpoint.replaceAll("/+$", "") : "http://127.0.0.1:9000";
        this.bucketName = (bucketName != null && !bucketName.isBlank()) ? bucketName : "saas-bucket";
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.publicBaseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl.replaceAll("/+$", "")
                : this.endpoint + "/" + this.bucketName;
        this.region = (region != null && !region.isBlank()) ? region.trim() : "us-east-1";
        this.environment = environment;
    }

    @Override
    public String upload(byte[] content, String path, String contentType) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String targetUrl = endpoint + "/" + bucketName + "/" + cleanPath;
        byte[] safeBytes = content != null ? content : new byte[0];
        try {
            URI uri = validateEndpointUri(targetUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            Map<String, String> headersToSign = new LinkedHashMap<>();
            if (contentType != null && !contentType.isBlank()) {
                conn.setRequestProperty("Content-Type", contentType);
                headersToSign.put("Content-Type", contentType);
            }
            conn.setRequestProperty("Content-Length", String.valueOf(safeBytes.length));
            applySigV4Headers(conn, "PUT", uri, headersToSign, safeBytes);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(safeBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("[S3Client] 文件上传成功: {} (Status: {})", targetUrl, responseCode);
                return getPublicUrl(cleanPath);
            }
            throw new BizException("FILE_STORAGE_UPLOAD_FAILED",
                    "云存储上传失败 (" + targetUrl + ")，HTTP 状态码: " + responseCode);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BizException("FILE_STORAGE_UPLOAD_ERROR",
                    "连接云存储上传异常 (" + targetUrl + "): " + e.getMessage());
        }
    }

    @Override
    public byte[] download(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String targetUrl = endpoint + "/" + bucketName + "/" + cleanPath;
        try {
            URI uri = validateEndpointUri(targetUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            applySigV4Headers(conn, "GET", uri, Map.of(), new byte[0]);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    return is.readAllBytes();
                }
            }
            throw new BizException("FILE_DOWNLOAD_FAILED", "云存储对象下载失败，响应码: " + responseCode);
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
            URI uri = validateEndpointUri(targetUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            applySigV4Headers(conn, "DELETE", uri, Map.of(), new byte[0]);
            int code = conn.getResponseCode();
            if (code >= 400 && code != 404) {
                throw new BizException("FILE_DELETE_FAILED", "云存储对象删除失败，HTTP 状态码: " + code);
            }
            log.info("[S3Client] 云存储对象已删除: {}", targetUrl);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BizException("FILE_DELETE_ERROR", "删除云存储对象异常: " + e.getMessage());
        }
    }

    @Override
    public String getPublicUrl(String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        return publicBaseUrl + "/" + cleanPath;
    }

    @Override
    public String generatePresignedUploadUrl(String path, int expireSeconds) {
        return presign("PUT", path, expireSeconds);
    }

    @Override
    public String generatePresignedDownloadUrl(String path, int expireSeconds) {
        return presign("GET", path, expireSeconds);
    }

    private String presign(String method, String path, int expireSeconds) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BizException("FILE_STORAGE_CREDENTIALS_MISSING",
                    "生成预签名 URL 需要为存储源配置有效的 AccessKey 与 SecretKey");
        }
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        URI uri = validateEndpointUri(endpoint + "/" + bucketName + "/" + cleanPath);
        return AwsSigV4Signer.presignUrl(method, uri, accessKey.trim(), secretKey.trim(), region, expireSeconds);
    }

    private URI validateEndpointUri(String targetUrl) {
        boolean allowPrivate = environment == null || !environment.matchesProfiles("prod");
        return SsrfProtectionValidator.validateOutboundUri(targetUrl, allowPrivate);
    }

    private void applySigV4Headers(
            HttpURLConnection conn,
            String method,
            URI uri,
            Map<String, String> headers,
            byte[] payload
    ) {
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            Map<String, String> signed = AwsSigV4Signer.signHeaders(
                    method, uri, headers, payload, accessKey.trim(), secretKey.trim(), region);
            signed.forEach(conn::setRequestProperty);
        }
    }
}
