package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("file_storage")
public class FileStorageEntity extends BaseTenantEntity {

    @TableField("storage_code")
    private String storageCode;

    @TableField("storage_name")
    private String storageName;

    @TableField("storage_type")
    private String storageType;

    @TableField("endpoint")
    private String endpoint;

    @TableField("bucket_default")
    private String bucketDefault;

    @TableField("region_name")
    private String regionName;

    @TableField("access_key")
    private String accessKey;

    @TableField("secret_cipher")
    private String secretCipher;

    @TableField("base_path")
    private String basePath;

    @TableField("default_folder")
    private String defaultFolder;

    @TableField("path_pattern")
    private String pathPattern;

    @TableField("max_file_mb")
    private Integer maxFileMb;

    @TableField("allowed_exts")
    private String allowedExts;

    @TableField("public_base_url")
    private String publicBaseUrl;

    @TableField("status")
    private String status;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("upload_mode")
    private String uploadMode;

    public String getStorageCode() {
        return storageCode;
    }

    public void setStorageCode(String storageCode) {
        this.storageCode = storageCode;
    }

    public String getStorageName() {
        return storageName;
    }

    public void setStorageName(String storageName) {
        this.storageName = storageName;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucketDefault() {
        return bucketDefault;
    }

    public void setBucketDefault(String bucketDefault) {
        this.bucketDefault = bucketDefault;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher) {
        this.secretCipher = secretCipher;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getDefaultFolder() {
        return defaultFolder;
    }

    public void setDefaultFolder(String defaultFolder) {
        this.defaultFolder = defaultFolder;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public Integer getMaxFileMb() {
        return maxFileMb;
    }

    public void setMaxFileMb(Integer maxFileMb) {
        this.maxFileMb = maxFileMb;
    }

    public String getAllowedExts() {
        return allowedExts;
    }

    public void setAllowedExts(String allowedExts) {
        this.allowedExts = allowedExts;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(String uploadMode) {
        this.uploadMode = uploadMode;
    }
}
