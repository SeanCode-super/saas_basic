package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_position")
public class PositionEntity extends AbstractOrganizationResourceEntity {

    @TableField("organization_id")
    private Long organizationId;

    @TableField("org_unit_id")
    private Long orgUnitId;

    @TableField("position_code")
    private String positionCode;

    @TableField("display_name")
    private String displayName;

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

    public String getPositionCode() {
        return positionCode;
    }

    public void setPositionCode(String positionCode) {
        this.positionCode = positionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
