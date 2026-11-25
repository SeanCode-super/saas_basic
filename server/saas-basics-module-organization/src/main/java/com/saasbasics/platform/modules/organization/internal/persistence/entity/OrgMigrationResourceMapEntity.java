package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("org_migration_resource_map")
public class OrgMigrationResourceMapEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("migration_scope_id")
    private Long migrationScopeId;

    @TableField("source_namespace")
    private String sourceNamespace;

    @TableField("source_resource_type")
    private String sourceResourceType;

    @TableField("source_resource_key")
    private String sourceResourceKey;

    @TableField("target_resource_type")
    private String targetResourceType;

    @TableField("target_organization_id")
    private Long targetOrganizationId;

    @TableField("target_resource_id")
    private Long targetResourceId;

    @TableField("target_public_id")
    private String targetPublicId;

    @TableField("planned_resource_code")
    private String plannedResourceCode;

    @TableField("mapping_origin")
    private String mappingOrigin;

    @TableField("managed_target")
    private Boolean managedTarget;

    @TableField("source_fingerprint")
    private String sourceFingerprint;

    @TableField("target_fingerprint")
    private String targetFingerprint;

    @TableField("target_version")
    private Integer targetVersion;

    @TableField("last_run_id")
    private Long lastRunId;

    @TableField("last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getMigrationScopeId() { return migrationScopeId; }
    public void setMigrationScopeId(Long migrationScopeId) { this.migrationScopeId = migrationScopeId; }
    public String getSourceNamespace() { return sourceNamespace; }
    public void setSourceNamespace(String sourceNamespace) { this.sourceNamespace = sourceNamespace; }
    public String getSourceResourceType() { return sourceResourceType; }
    public void setSourceResourceType(String sourceResourceType) { this.sourceResourceType = sourceResourceType; }
    public String getSourceResourceKey() { return sourceResourceKey; }
    public void setSourceResourceKey(String sourceResourceKey) { this.sourceResourceKey = sourceResourceKey; }
    public String getTargetResourceType() { return targetResourceType; }
    public void setTargetResourceType(String targetResourceType) { this.targetResourceType = targetResourceType; }
    public Long getTargetOrganizationId() { return targetOrganizationId; }
    public void setTargetOrganizationId(Long targetOrganizationId) { this.targetOrganizationId = targetOrganizationId; }
    public Long getTargetResourceId() { return targetResourceId; }
    public void setTargetResourceId(Long targetResourceId) { this.targetResourceId = targetResourceId; }
    public String getTargetPublicId() { return targetPublicId; }
    public void setTargetPublicId(String targetPublicId) { this.targetPublicId = targetPublicId; }
    public String getPlannedResourceCode() { return plannedResourceCode; }
    public void setPlannedResourceCode(String plannedResourceCode) { this.plannedResourceCode = plannedResourceCode; }
    public String getMappingOrigin() { return mappingOrigin; }
    public void setMappingOrigin(String mappingOrigin) { this.mappingOrigin = mappingOrigin; }
    public Boolean getManagedTarget() { return managedTarget; }
    public void setManagedTarget(Boolean managedTarget) { this.managedTarget = managedTarget; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public String getTargetFingerprint() { return targetFingerprint; }
    public void setTargetFingerprint(String targetFingerprint) { this.targetFingerprint = targetFingerprint; }
    public Integer getTargetVersion() { return targetVersion; }
    public void setTargetVersion(Integer targetVersion) { this.targetVersion = targetVersion; }
    public Long getLastRunId() { return lastRunId; }
    public void setLastRunId(Long lastRunId) { this.lastRunId = lastRunId; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
