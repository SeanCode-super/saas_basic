package com.saasbasics.platform.modules.audit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;
import java.time.LocalDateTime;

@TableName("sec_blacklist")
public class SecBlacklistEntity extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("black_type")
    private String blackType;

    @TableField("black_value")
    private String blackValue;

    @TableField("reason")
    private String reason;

    @TableField("expired_at")
    private LocalDateTime expiredAt;

    @TableField("status")
    private String status;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getBlackType() {
        return blackType;
    }

    public void setBlackType(String blackType) {
        this.blackType = blackType;
    }

    public String getBlackValue() {
        return blackValue;
    }

    public void setBlackValue(String blackValue) {
        this.blackValue = blackValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
