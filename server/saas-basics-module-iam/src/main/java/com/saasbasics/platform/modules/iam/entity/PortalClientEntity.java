package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_portal_client")
public class PortalClientEntity extends BaseTenantEntity {

    @TableField("client_id")
    private String clientId;

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("client_name")
    private String clientName;

    @TableField("portal_title")
    private String portalTitle;

    @TableField("welcome_title")
    private String welcomeTitle;

    @TableField("welcome_text")
    private String welcomeText;

    @TableField("logo_url")
    private String logoUrl;

    @TableField("theme_code")
    private String themeCode;

    @TableField("background_image_url")
    private String backgroundImageUrl;

    @TableField("background_color")
    private String backgroundColor;

    @TableField("filing_info")
    private String filingInfo;

    @TableField("login_policy_id")
    private Long loginPolicyId;

    @TableField("password_policy_id")
    private Long passwordPolicyId;

    @TableField("captcha_mode")
    private String captchaMode;

    @TableField("slider_reserved")
    private Boolean sliderReserved;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("status")
    private String status;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getPortalTitle() {
        return portalTitle;
    }

    public void setPortalTitle(String portalTitle) {
        this.portalTitle = portalTitle;
    }

    public String getWelcomeTitle() {
        return welcomeTitle;
    }

    public void setWelcomeTitle(String welcomeTitle) {
        this.welcomeTitle = welcomeTitle;
    }

    public String getWelcomeText() {
        return welcomeText;
    }

    public void setWelcomeText(String welcomeText) {
        this.welcomeText = welcomeText;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getThemeCode() {
        return themeCode;
    }

    public void setThemeCode(String themeCode) {
        this.themeCode = themeCode;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getFilingInfo() {
        return filingInfo;
    }

    public void setFilingInfo(String filingInfo) {
        this.filingInfo = filingInfo;
    }

    public Long getLoginPolicyId() {
        return loginPolicyId;
    }

    public void setLoginPolicyId(Long loginPolicyId) {
        this.loginPolicyId = loginPolicyId;
    }

    public Long getPasswordPolicyId() {
        return passwordPolicyId;
    }

    public void setPasswordPolicyId(Long passwordPolicyId) {
        this.passwordPolicyId = passwordPolicyId;
    }

    public String getCaptchaMode() {
        return captchaMode;
    }

    public void setCaptchaMode(String captchaMode) {
        this.captchaMode = captchaMode;
    }

    public Boolean getSliderReserved() {
        return sliderReserved;
    }

    public void setSliderReserved(Boolean sliderReserved) {
        this.sliderReserved = sliderReserved;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
