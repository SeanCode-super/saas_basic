package com.saasbasics.platform.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;
import java.time.LocalDateTime;

@TableName("plat_tenant")
public class TenantEntity extends BaseEntity {

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("tenant_name")
    private String tenantName;

    @TableField("tenant_type")
    private String tenantType;

    @TableField("package_id")
    private Long packageId;

    @TableField("extra_features_json")
    private String extraFeaturesJson;

    @TableField("disabled_features_json")
    private String disabledFeaturesJson;

    @TableField("status")
    private String status;

    @TableField("isolation_mode")
    private String isolationMode;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_mobile")
    private String contactMobile;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantType() {
        return tenantType;
    }

    public void setTenantType(String tenantType) {
        this.tenantType = tenantType;
    }

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIsolationMode() {
        return isolationMode;
    }

    public void setIsolationMode(String isolationMode) {
        this.isolationMode = isolationMode;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactMobile() {
        return contactMobile;
    }

    public void setContactMobile(String contactMobile) {
        this.contactMobile = contactMobile;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getExtraFeaturesJson() {
        return extraFeaturesJson;
    }

    public void setExtraFeaturesJson(String extraFeaturesJson) {
        this.extraFeaturesJson = extraFeaturesJson;
    }

    public String getDisabledFeaturesJson() {
        return disabledFeaturesJson;
    }

    public void setDisabledFeaturesJson(String disabledFeaturesJson) {
        this.disabledFeaturesJson = disabledFeaturesJson;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
