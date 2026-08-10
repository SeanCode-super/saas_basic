package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("iam_session")
public class AuthSessionEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("session_no")
    private String sessionNo;

    @TableField("tenant_code")
    private String tenantCode;

    @TableField("user_id")
    private Long userId;

    @TableField("subject_binding_public_id")
    private String subjectBindingPublicId;

    @TableField("selected_assignment_public_id")
    private String selectedAssignmentPublicId;

    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("user_type")
    private String userType;

    @TableField("login_type")
    private String loginType;

    @TableField("access_token_hash")
    private String accessTokenHash;

    @TableField("login_ip")
    private String loginIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("last_access_at")
    private LocalDateTime lastAccessAt;

    @TableField("logout_at")
    private LocalDateTime logoutAt;

    @TableField("status")
    private String status;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getSubjectBindingPublicId() {
        return subjectBindingPublicId;
    }

    public void setSubjectBindingPublicId(String subjectBindingPublicId) {
        this.subjectBindingPublicId = subjectBindingPublicId;
    }

    public String getSelectedAssignmentPublicId() {
        return selectedAssignmentPublicId;
    }

    public void setSelectedAssignmentPublicId(String selectedAssignmentPublicId) {
        this.selectedAssignmentPublicId = selectedAssignmentPublicId;
    }

    public String getSessionNo() {
        return sessionNo;
    }

    public void setSessionNo(String sessionNo) {
        this.sessionNo = sessionNo;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getLastAccessAt() {
        return lastAccessAt;
    }

    public void setLastAccessAt(LocalDateTime lastAccessAt) {
        this.lastAccessAt = lastAccessAt;
    }

    public LocalDateTime getLogoutAt() {
        return logoutAt;
    }

    public void setLogoutAt(LocalDateTime logoutAt) {
        this.logoutAt = logoutAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
