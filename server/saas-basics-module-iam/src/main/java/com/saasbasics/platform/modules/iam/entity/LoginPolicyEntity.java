package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_login_policy")
public class LoginPolicyEntity extends BaseTenantEntity {

    @TableField("policy_code")
    private String policyCode;

    @TableField("policy_name")
    private String policyName;

    @TableField("allow_password_login")
    private Boolean allowPasswordLogin;

    @TableField("allow_sms_login")
    private Boolean allowSmsLogin;

    @TableField("allow_email_login")
    private Boolean allowEmailLogin;

    @TableField("allow_social_login")
    private Boolean allowSocialLogin;

    @TableField("force_mfa")
    private Boolean forceMfa;

    @TableField("session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    @TableField("max_failed_count")
    private Integer maxFailedCount;

    @TableField("lock_minutes")
    private Integer lockMinutes;

    @TableField("ip_allowlist_json")
    private String ipAllowlistJson;

    @TableField("device_trust_days")
    private Integer deviceTrustDays;

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

    public Boolean getAllowPasswordLogin() {
        return allowPasswordLogin;
    }

    public void setAllowPasswordLogin(Boolean allowPasswordLogin) {
        this.allowPasswordLogin = allowPasswordLogin;
    }

    public Boolean getForceMfa() {
        return forceMfa;
    }

    public void setForceMfa(Boolean forceMfa) {
        this.forceMfa = forceMfa;
    }

    public Boolean getAllowSmsLogin() {
        return allowSmsLogin;
    }

    public void setAllowSmsLogin(Boolean allowSmsLogin) {
        this.allowSmsLogin = allowSmsLogin;
    }

    public Boolean getAllowEmailLogin() {
        return allowEmailLogin;
    }

    public void setAllowEmailLogin(Boolean allowEmailLogin) {
        this.allowEmailLogin = allowEmailLogin;
    }

    public Boolean getAllowSocialLogin() {
        return allowSocialLogin;
    }

    public void setAllowSocialLogin(Boolean allowSocialLogin) {
        this.allowSocialLogin = allowSocialLogin;
    }

    public Integer getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(Integer sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public Integer getMaxFailedCount() {
        return maxFailedCount;
    }

    public void setMaxFailedCount(Integer maxFailedCount) {
        this.maxFailedCount = maxFailedCount;
    }

    public Integer getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(Integer lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public String getIpAllowlistJson() {
        return ipAllowlistJson;
    }

    public void setIpAllowlistJson(String ipAllowlistJson) {
        this.ipAllowlistJson = ipAllowlistJson;
    }

    public Integer getDeviceTrustDays() {
        return deviceTrustDays;
    }

    public void setDeviceTrustDays(Integer deviceTrustDays) {
        this.deviceTrustDays = deviceTrustDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
