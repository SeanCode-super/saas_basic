package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("file_upload_route")
public class FileUploadRouteEntity extends BaseTenantEntity {

    @TableField("storage_id")
    private Long storageId;

    @TableField("biz_type")
    private String bizType;

    @TableField("route_name")
    private String routeName;

    @TableField("upload_mode")
    private String uploadMode;

    @TableField("target_dir")
    private String targetDir;

    @TableField("path_pattern")
    private String pathPattern;

    @TableField("max_file_mb")
    private Integer maxFileMb;

    @TableField("allowed_exts")
    private String allowedExts;

    @TableField("priority")
    private Integer priority;

    @TableField("status")
    private String status;

    public Long getStorageId() {
        return storageId;
    }

    public void setStorageId(Long storageId) {
        this.storageId = storageId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(String uploadMode) {
        this.uploadMode = uploadMode;
    }

    public String getTargetDir() {
        return targetDir;
    }

    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
