package com.saasbasics.platform.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("file_lifecycle_policy")
public class FileLifecyclePolicyEntity extends BaseTenantEntity {

    @TableField("policy_code")
    private String policyCode;

    @TableField("policy_name")
    private String policyName;

    @TableField("file_scope")
    private String fileScope;

    @TableField("retention_days")
    private Integer retentionDays;

    @TableField("archive_after_days")
    private Integer archiveAfterDays;

    @TableField("delete_after_days")
    private Integer deleteAfterDays;

    @TableField("deduplicate_enabled")
    private Boolean deduplicateEnabled;

    @TableField("version_retention_count")
    private Integer versionRetentionCount;

    @TableField("status")
    private String status;

    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getFileScope() { return fileScope; }
    public void setFileScope(String fileScope) { this.fileScope = fileScope; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public Integer getArchiveAfterDays() { return archiveAfterDays; }
    public void setArchiveAfterDays(Integer archiveAfterDays) { this.archiveAfterDays = archiveAfterDays; }
    public Integer getDeleteAfterDays() { return deleteAfterDays; }
    public void setDeleteAfterDays(Integer deleteAfterDays) { this.deleteAfterDays = deleteAfterDays; }
    public Boolean getDeduplicateEnabled() { return deduplicateEnabled; }
    public void setDeduplicateEnabled(Boolean deduplicateEnabled) { this.deduplicateEnabled = deduplicateEnabled; }
    public Integer getVersionRetentionCount() { return versionRetentionCount; }
    public void setVersionRetentionCount(Integer versionRetentionCount) { this.versionRetentionCount = versionRetentionCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
