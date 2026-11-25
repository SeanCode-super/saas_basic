package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("org_migration_scope")
public class OrgMigrationScopeEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("source_namespace")
    private String sourceNamespace;

    @TableField("source_scope_type")
    private String sourceScopeType;

    @TableField("source_scope_key")
    private String sourceScopeKey;

    @TableField("target_organization_id")
    private Long targetOrganizationId;

    @TableField("target_parent_unit_id")
    private Long targetParentUnitId;

    @TableField("effective_at")
    private LocalDateTime effectiveAt;

    @TableField("configuration_fingerprint")
    private String configurationFingerprint;

    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getSourceNamespace() { return sourceNamespace; }
    public void setSourceNamespace(String sourceNamespace) { this.sourceNamespace = sourceNamespace; }
    public String getSourceScopeType() { return sourceScopeType; }
    public void setSourceScopeType(String sourceScopeType) { this.sourceScopeType = sourceScopeType; }
    public String getSourceScopeKey() { return sourceScopeKey; }
    public void setSourceScopeKey(String sourceScopeKey) { this.sourceScopeKey = sourceScopeKey; }
    public Long getTargetOrganizationId() { return targetOrganizationId; }
    public void setTargetOrganizationId(Long targetOrganizationId) { this.targetOrganizationId = targetOrganizationId; }
    public Long getTargetParentUnitId() { return targetParentUnitId; }
    public void setTargetParentUnitId(Long targetParentUnitId) { this.targetParentUnitId = targetParentUnitId; }
    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }
    public String getConfigurationFingerprint() { return configurationFingerprint; }
    public void setConfigurationFingerprint(String configurationFingerprint) { this.configurationFingerprint = configurationFingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
