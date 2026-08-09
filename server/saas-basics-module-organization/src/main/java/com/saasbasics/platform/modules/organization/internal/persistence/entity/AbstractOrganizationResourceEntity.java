package com.saasbasics.platform.modules.organization.internal.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

public abstract class AbstractOrganizationResourceEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("status")
    private String status;

    @TableField("valid_from")
    private LocalDateTime validFrom;

    @TableField("valid_to")
    private LocalDateTime validTo;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }
}
