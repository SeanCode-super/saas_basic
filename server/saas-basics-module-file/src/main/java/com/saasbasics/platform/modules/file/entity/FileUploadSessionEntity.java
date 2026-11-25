package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("file_upload_session")
public class FileUploadSessionEntity extends BaseTenantEntity {

    @TableField("bucket_id")
    private Long bucketId;

    @TableField("storage_id")
    private Long storageId;

    @TableField("session_code")
    private String sessionCode;

    @TableField("object_key")
    private String objectKey;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("upload_mode")
    private String uploadMode;

    @TableField("part_count")
    private Integer partCount;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("status")
    private String status;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    public Long getBucketId() {
        return bucketId;
    }

    public void setBucketId(Long bucketId) {
        this.bucketId = bucketId;
    }

    public Long getStorageId() {
        return storageId;
    }

    public void setStorageId(Long storageId) {
        this.storageId = storageId;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(String uploadMode) {
        this.uploadMode = uploadMode;
    }

    public Integer getPartCount() {
        return partCount;
    }

    public void setPartCount(Integer partCount) {
        this.partCount = partCount;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @TableField("request_key")
    private String requestKey;

    @TableField("request_hash")
    private String requestHash;

    @TableField("file_object_id")
    private Long fileObjectId;

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Long getFileObjectId() { return fileObjectId; }
    public void setFileObjectId(Long fileObjectId) { this.fileObjectId = fileObjectId; }

    @TableField("quota_managed")
    private Integer quotaManaged;

    @TableField("cleanup_after")
    private LocalDateTime cleanupAfter;

    public Integer getQuotaManaged() { return quotaManaged; }
    public void setQuotaManaged(Integer quotaManaged) { this.quotaManaged = quotaManaged; }
    public LocalDateTime getCleanupAfter() { return cleanupAfter; }
    public void setCleanupAfter(LocalDateTime cleanupAfter) { this.cleanupAfter = cleanupAfter; }
}
