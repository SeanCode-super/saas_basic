package com.saasbasics.platform.modules.ai.application.dto;

import java.time.LocalDateTime;

public class AiProviderDTO {

    private Long id;
    private Long tenantId;
    private String providerCode;
    private String providerName;
    private String providerType;
    private String baseUrl;
    private String apiKeyMasked;
    private boolean hasApiKey;
    private boolean encryptedAtRest;
    private String keyVersion;
    private String cipherDigest;
    private Integer rotationCount;
    private String status;
    private Integer isDefault;
    private Integer sortNo;
    private String remark;
    private Integer modelCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyMasked() { return apiKeyMasked; }
    public void setApiKeyMasked(String apiKeyMasked) { this.apiKeyMasked = apiKeyMasked; }
    public boolean isHasApiKey() { return hasApiKey; }
    public void setHasApiKey(boolean hasApiKey) { this.hasApiKey = hasApiKey; }
    public boolean isEncryptedAtRest() { return encryptedAtRest; }
    public void setEncryptedAtRest(boolean encryptedAtRest) { this.encryptedAtRest = encryptedAtRest; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public String getCipherDigest() { return cipherDigest; }
    public void setCipherDigest(String cipherDigest) { this.cipherDigest = cipherDigest; }
    public Integer getRotationCount() { return rotationCount; }
    public void setRotationCount(Integer rotationCount) { this.rotationCount = rotationCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getModelCount() { return modelCount; }
    public void setModelCount(Integer modelCount) { this.modelCount = modelCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
