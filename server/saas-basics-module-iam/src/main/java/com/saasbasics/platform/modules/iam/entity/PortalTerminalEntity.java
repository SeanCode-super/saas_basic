package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("iam_portal_terminal")
public class PortalTerminalEntity extends BaseTenantEntity {

    @TableField("portal_client_id")
    private Long portalClientId;

    @TableField("terminal_code")
    private String terminalCode;

    @TableField("terminal_name")
    private String terminalName;

    @TableField("terminal_type")
    private String terminalType;

    @TableField("portal_title")
    private String portalTitle;

    @TableField("logo_url")
    private String logoUrl;

    @TableField("theme_code")
    private String themeCode;

    @TableField("background_image_url")
    private String backgroundImageUrl;

    @TableField("background_color")
    private String backgroundColor;

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

    public Long getPortalClientId() {
        return portalClientId;
    }

    public void setPortalClientId(Long portalClientId) {
        this.portalClientId = portalClientId;
    }

    public String getTerminalCode() {
        return terminalCode;
    }

    public void setTerminalCode(String terminalCode) {
        this.terminalCode = terminalCode;
    }

    public String getTerminalName() {
        return terminalName;
    }

    public void setTerminalName(String terminalName) {
        this.terminalName = terminalName;
    }

    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    public String getPortalTitle() {
        return portalTitle;
    }

    public void setPortalTitle(String portalTitle) {
        this.portalTitle = portalTitle;
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
