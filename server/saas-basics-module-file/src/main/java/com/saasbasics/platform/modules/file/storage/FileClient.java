package com.saasbasics.platform.modules.file.storage;

/**
 * 平台统一文件存储客户端 SPI 接口。
 * 屏蔽底层实现差异（Local 本地存储、MinIO、阿里云 OSS、腾讯云 COS、华为云 OBS 等）。
 */
public interface FileClient {

    /**
     * 上传文件
     *
     * @param content     文件字节内容
     * @param path        存储路径 / 对象键 (例如 "avatars/2026/user-1.png")
     * @param contentType MIME 类型
     * @return 文件的公网可访问 URL
     */
    String upload(byte[] content, String path, String contentType);

    /**
     * 下载文件内容
     *
     * @param path 存储路径 / 对象键
     * @return 文件字节内容
     */
    byte[] download(String path);

    /**
     * 删除文件
     *
     * @param path 存储路径 / 对象键
     */
    void delete(String path);

    /**
     * 获取文件访问 URL
     *
     * @param path 存储路径 / 对象键
     * @return 完整 URL
     */
    String getPublicUrl(String path);
}
