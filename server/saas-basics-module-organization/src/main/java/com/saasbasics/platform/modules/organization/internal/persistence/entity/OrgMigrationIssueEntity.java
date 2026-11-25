package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("org_migration_issue")
public class OrgMigrationIssueEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("migration_scope_id")
    private Long migrationScopeId;

    @TableField("migration_run_id")
    private Long migrationRunId;

    @TableField("resource_map_id")
    private Long resourceMapId;

    @TableField("source_resource_type")
    private String sourceResourceType;

    @TableField("source_resource_key")
    private String sourceResourceKey;

    @TableField("issue_code")
    private String issueCode;

    @TableField("severity")
    private String severity;

    @TableField("field_name")
    private String fieldName;

    @TableField("expected_fingerprint")
    private String expectedFingerprint;

    @TableField("actual_fingerprint")
    private String actualFingerprint;

    @TableField("detail")
    private String detail;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getMigrationScopeId() { return migrationScopeId; }
    public void setMigrationScopeId(Long migrationScopeId) { this.migrationScopeId = migrationScopeId; }
    public Long getMigrationRunId() { return migrationRunId; }
    public void setMigrationRunId(Long migrationRunId) { this.migrationRunId = migrationRunId; }
    public Long getResourceMapId() { return resourceMapId; }
    public void setResourceMapId(Long resourceMapId) { this.resourceMapId = resourceMapId; }
    public String getSourceResourceType() { return sourceResourceType; }
    public void setSourceResourceType(String sourceResourceType) { this.sourceResourceType = sourceResourceType; }
    public String getSourceResourceKey() { return sourceResourceKey; }
    public void setSourceResourceKey(String sourceResourceKey) { this.sourceResourceKey = sourceResourceKey; }
    public String getIssueCode() { return issueCode; }
    public void setIssueCode(String issueCode) { this.issueCode = issueCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getExpectedFingerprint() { return expectedFingerprint; }
    public void setExpectedFingerprint(String expectedFingerprint) { this.expectedFingerprint = expectedFingerprint; }
    public String getActualFingerprint() { return actualFingerprint; }
    public void setActualFingerprint(String actualFingerprint) { this.actualFingerprint = actualFingerprint; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
