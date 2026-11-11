package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("ai_credential_ref")
public class AiCredentialRefEntity extends BaseTenantEntity {

    private String credentialCode;
    private Long providerId;
    private String keyAlias;
    private String maskedHint;
    private String encryptionAlgo;
    private String keyVersion;
    private String cipherDigest;
    private Integer rotationCount;
    private LocalDateTime lastRotatedAt;
    private String status;

    public String getCredentialCode() { return credentialCode; }
    public void setCredentialCode(String credentialCode) { this.credentialCode = credentialCode; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getKeyAlias() { return keyAlias; }
    public void setKeyAlias(String keyAlias) { this.keyAlias = keyAlias; }
    public String getMaskedHint() { return maskedHint; }
    public void setMaskedHint(String maskedHint) { this.maskedHint = maskedHint; }
    public String getEncryptionAlgo() { return encryptionAlgo; }
    public void setEncryptionAlgo(String encryptionAlgo) { this.encryptionAlgo = encryptionAlgo; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public String getCipherDigest() { return cipherDigest; }
    public void setCipherDigest(String cipherDigest) { this.cipherDigest = cipherDigest; }
    public Integer getRotationCount() { return rotationCount; }
    public void setRotationCount(Integer rotationCount) { this.rotationCount = rotationCount; }
    public LocalDateTime getLastRotatedAt() { return lastRotatedAt; }
    public void setLastRotatedAt(LocalDateTime lastRotatedAt) { this.lastRotatedAt = lastRotatedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
