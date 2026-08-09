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

    @TableField("public_base_url")
    private String publicBaseUrl;

    @TableField("status")
    private String status;

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
}
