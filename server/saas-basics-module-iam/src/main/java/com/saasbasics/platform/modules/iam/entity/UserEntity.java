package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("iam_user")
public class UserEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("user_code")
    private String userCode;

    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("employee_id")
    private Long employeeId;

    @TableField("user_type")
    private String userType;

    @TableField("status")
    private String status;

    @TableField("avatar_file_id")
    private Long avatarFileId;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("mobile")
    private String mobile;

    @TableField("email")
    private String email;

    @TableField("gender")
    private String gender;

    @TableField("bio")
    private String bio;

    @TableField("work_location")
    private String workLocation;

    @TableField("ext_contact")
    private String extContact;

    @TableField("locale")
    private String locale;

    @TableField("timezone")
    private String timezone;

    @TableField("preferences_json")
    private String preferencesJson;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("password_changed_at")
    private LocalDateTime passwordChangedAt;

    @TableField("need_reset_password")
    private Boolean needResetPassword;

    @TableField("mfa_enabled")
    private Boolean mfaEnabled;

    @TableField("mfa_secret_enc")
    private String mfaSecretEnc;

    @TableField("mfa_recovery_codes_enc")
    private String mfaRecoveryCodesEnc;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public String getExtContact() {
        return extContact;
    }

    public void setExtContact(String extContact) {
        this.extContact = extContact;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getPreferencesJson() {
        return preferencesJson;
    }

    public void setPreferencesJson(String preferencesJson) {
        this.preferencesJson = preferencesJson;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public Boolean getNeedResetPassword() {
        return needResetPassword;
    }

    public void setNeedResetPassword(Boolean needResetPassword) {
        this.needResetPassword = needResetPassword;
    }

    public Boolean getMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(Boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public String getMfaSecretEnc() {
        return mfaSecretEnc;
    }

    public void setMfaSecretEnc(String mfaSecretEnc) {
        this.mfaSecretEnc = mfaSecretEnc;
    }

    public String getMfaRecoveryCodesEnc() {
        return mfaRecoveryCodesEnc;
    }

    public void setMfaRecoveryCodesEnc(String mfaRecoveryCodesEnc) {
        this.mfaRecoveryCodesEnc = mfaRecoveryCodesEnc;
    }
}
