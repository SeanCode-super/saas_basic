package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_organization_classification")
public class OrganizationClassificationEntity extends AbstractTypedResourceEntity {

    @TableField("organization_id")
    private Long organizationId;

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }
}
