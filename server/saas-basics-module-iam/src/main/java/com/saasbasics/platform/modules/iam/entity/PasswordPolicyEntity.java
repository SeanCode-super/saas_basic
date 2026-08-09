package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_password_policy")
public class PasswordPolicyEntity extends BaseTenantEntity {

    @TableField("policy_code")
    private String policyCode;

    @TableField("policy_name")
    private String policyName;

    @TableField("min_length")
    private Integer minLength;

    @TableField("max_length")
    private Integer maxLength;

    @TableField("require_uppercase")
    private Boolean requireUppercase;

    @TableField("require_lowercase")
    private Boolean requireLowercase;

    @TableField("require_number")
    private Boolean requireNumber;

    @TableField("require_special")
    private Boolean requireSpecial;

    @TableField("password_history_limit")
    private Integer passwordHistoryLimit;

    @TableField("password_expire_days")
    private Integer passwordExpireDays;

    @TableField("temp_password_expire_hours")
    private Integer tempPasswordExpireHours;

    @TableField("status")
    private String status;

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Boolean getRequireUppercase() {
        return requireUppercase;
    }

    public void setRequireUppercase(Boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
    }

    public Boolean getRequireLowercase() {
        return requireLowercase;
    }

    public void setRequireLowercase(Boolean requireLowercase) {
        this.requireLowercase = requireLowercase;
    }

    public Boolean getRequireNumber() {
        return requireNumber;
    }

    public void setRequireNumber(Boolean requireNumber) {
        this.requireNumber = requireNumber;
    }

    public Boolean getRequireSpecial() {
        return requireSpecial;
    }

    public void setRequireSpecial(Boolean requireSpecial) {
        this.requireSpecial = requireSpecial;
    }

    public Integer getPasswordHistoryLimit() {
        return passwordHistoryLimit;
    }

    public void setPasswordHistoryLimit(Integer passwordHistoryLimit) {
        this.passwordHistoryLimit = passwordHistoryLimit;
    }

    public Integer getPasswordExpireDays() {
        return passwordExpireDays;
    }

    public void setPasswordExpireDays(Integer passwordExpireDays) {
        this.passwordExpireDays = passwordExpireDays;
    }

    public Integer getTempPasswordExpireHours() {
        return tempPasswordExpireHours;
    }

    public void setTempPasswordExpireHours(Integer tempPasswordExpireHours) {
        this.tempPasswordExpireHours = tempPasswordExpireHours;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
