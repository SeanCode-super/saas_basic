package com.saasbasics.platform.common.model;

import com.baomidou.mybatisplus.annotation.TableField;

public abstract class BaseTenantEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
