package com.saasbasics.platform.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("iam_webauthn_credential")
public class WebAuthnCredentialEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("user_id")
    private Long userId;

    @TableField("credential_id")
    private String credentialId;

    @TableField("public_key_spki")
    private String publicKeySpki;

    @TableField("algorithm")
    private Integer algorithm;

    @TableField("sign_count")
    private Long signCount;

    @TableField("aaguid")
    private String aaguid;

    @TableField("transports")
    private String transports;

    @TableField("device_name")
    private String deviceName;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField("status")
    private String status;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getPublicKeySpki() { return publicKeySpki; }
    public void setPublicKeySpki(String publicKeySpki) { this.publicKeySpki = publicKeySpki; }
    public Integer getAlgorithm() { return algorithm; }
    public void setAlgorithm(Integer algorithm) { this.algorithm = algorithm; }
    public Long getSignCount() { return signCount; }
    public void setSignCount(Long signCount) { this.signCount = signCount; }
    public String getAaguid() { return aaguid; }
    public void setAaguid(String aaguid) { this.aaguid = aaguid; }
    public String getTransports() { return transports; }
    public void setTransports(String transports) { this.transports = transports; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
