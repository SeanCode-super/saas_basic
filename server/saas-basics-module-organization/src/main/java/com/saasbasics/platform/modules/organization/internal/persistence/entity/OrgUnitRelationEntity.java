package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("org_unit_relation")
public class OrgUnitRelationEntity extends AbstractTypedResourceEntity {

    @TableField("source_unit_id")
    private Long sourceUnitId;

    @TableField("target_unit_id")
    private Long targetUnitId;

    public Long getSourceUnitId() {
        return sourceUnitId;
    }

    public void setSourceUnitId(Long sourceUnitId) {
        this.sourceUnitId = sourceUnitId;
    }

    public Long getTargetUnitId() {
        return targetUnitId;
    }

    public void setTargetUnitId(Long targetUnitId) {
        this.targetUnitId = targetUnitId;
    }
}
