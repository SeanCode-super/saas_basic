package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_organization_relation")
public class OrganizationRelationEntity extends AbstractTypedResourceEntity {

    @TableField("source_organization_id")
    private Long sourceOrganizationId;

    @TableField("target_organization_id")
    private Long targetOrganizationId;

    public Long getSourceOrganizationId() {
        return sourceOrganizationId;
    }

    public void setSourceOrganizationId(Long sourceOrganizationId) {
        this.sourceOrganizationId = sourceOrganizationId;
    }

    public Long getTargetOrganizationId() {
        return targetOrganizationId;
    }

    public void setTargetOrganizationId(Long targetOrganizationId) {
        this.targetOrganizationId = targetOrganizationId;
    }
}
