package com.saasbasics.platform.common.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;

public abstract class BaseTenantEntity extends BaseEntity {

    @TableField(value = "tenant_id", fill = FieldFill.INSERT_UPDATE)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
