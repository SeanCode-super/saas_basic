package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("org_migration_run")
public class OrgMigrationRunEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("migration_scope_id")
    private Long migrationScopeId;

    @TableField("migration_revision")
    private String migrationRevision;

    @TableField("run_mode")
    private String runMode;

    @TableField("snapshot_at")
    private LocalDateTime snapshotAt;

    @TableField("source_fingerprint")
    private String sourceFingerprint;

    @TableField("source_resource_count")
    private Long sourceResourceCount;

    @TableField("mapped_resource_count")
    private Long mappedResourceCount;

    @TableField("blocking_issue_count")
    private Long blockingIssueCount;

    @TableField("warning_issue_count")
    private Long warningIssueCount;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("failure_code")
    private String failureCode;

    @TableField("failure_detail")
    private String failureDetail;

    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getMigrationScopeId() { return migrationScopeId; }
    public void setMigrationScopeId(Long migrationScopeId) { this.migrationScopeId = migrationScopeId; }
    public String getMigrationRevision() { return migrationRevision; }
    public void setMigrationRevision(String migrationRevision) { this.migrationRevision = migrationRevision; }
    public String getRunMode() { return runMode; }
    public void setRunMode(String runMode) { this.runMode = runMode; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public Long getSourceResourceCount() { return sourceResourceCount; }
    public void setSourceResourceCount(Long sourceResourceCount) { this.sourceResourceCount = sourceResourceCount; }
    public Long getMappedResourceCount() { return mappedResourceCount; }
    public void setMappedResourceCount(Long mappedResourceCount) { this.mappedResourceCount = mappedResourceCount; }
    public Long getBlockingIssueCount() { return blockingIssueCount; }
    public void setBlockingIssueCount(Long blockingIssueCount) { this.blockingIssueCount = blockingIssueCount; }
    public Long getWarningIssueCount() { return warningIssueCount; }
    public void setWarningIssueCount(Long warningIssueCount) { this.warningIssueCount = warningIssueCount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureDetail() { return failureDetail; }
    public void setFailureDetail(String failureDetail) { this.failureDetail = failureDetail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
