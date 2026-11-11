package com.saasbasics.platform.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;
import java.time.LocalDateTime;

@TableName("sys_transport_crypto_key")
public class TransportCryptoKeyEntity extends BaseEntity {

    @TableField("key_id")
    private String keyId;

    @TableField("key_source")
    private String keySource;

    @TableField("sealed_private_key")
    private String sealedPrivateKey;

    @TableField("public_key_base64")
    private String publicKeyBase64;

    @TableField("fingerprint_sha256")
    private String fingerprintSha256;

    @TableField("is_active")
    private Integer isActive;

    @TableField("issued_at")
    private LocalDateTime issuedAt;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySource() {
        return keySource;
    }

    public void setKeySource(String keySource) {
        this.keySource = keySource;
    }

    public String getSealedPrivateKey() {
        return sealedPrivateKey;
    }

    public void setSealedPrivateKey(String sealedPrivateKey) {
        this.sealedPrivateKey = sealedPrivateKey;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void setFingerprintSha256(String fingerprintSha256) {
        this.fingerprintSha256 = fingerprintSha256;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
}
