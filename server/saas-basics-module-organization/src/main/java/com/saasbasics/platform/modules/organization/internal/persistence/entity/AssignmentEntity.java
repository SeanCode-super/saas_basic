package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_assignment")
public class AssignmentEntity extends AbstractOrganizationResourceEntity {

    @TableField("engagement_id")
    private Long engagementId;

    @TableField("organization_id")
    private Long organizationId;

    @TableField("org_unit_id")
    private Long orgUnitId;

    @TableField("position_id")
    private Long positionId;

    @TableField("is_primary")
    private Boolean primaryAssignment;

    public Long getEngagementId() {
        return engagementId;
    }

    public void setEngagementId(Long engagementId) {
        this.engagementId = engagementId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public Boolean getPrimaryAssignment() {
        return primaryAssignment;
    }

    public void setPrimaryAssignment(Boolean primaryAssignment) {
        this.primaryAssignment = primaryAssignment;
    }
}
