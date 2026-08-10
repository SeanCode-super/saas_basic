package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_engagement")
public class EngagementEntity extends AbstractTypedResourceEntity {

    @TableField("engagement_code")
    private String engagementCode;

    @TableField("person_id")
    private Long personId;

    @TableField("organization_id")
    private Long organizationId;

    public String getEngagementCode() {
        return engagementCode;
    }

    public void setEngagementCode(String engagementCode) {
        this.engagementCode = engagementCode;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }
}
