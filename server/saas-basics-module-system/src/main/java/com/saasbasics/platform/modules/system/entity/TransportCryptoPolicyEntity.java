package com.saasbasics.platform.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseEntity;

@TableName("sys_transport_crypto_policy")
public class TransportCryptoPolicyEntity extends BaseEntity {

    @TableField("policy_code")
    private String policyCode;

    @TableField("mode")
    private String mode;

    @TableField("scope_mode")
    private String scopeMode;

    @TableField("algorithm")
    private String algorithm;

    @TableField("encrypt_request")
    private Integer encryptRequest;

    @TableField("encrypt_response")
    private Integer encryptResponse;

    @TableField("encrypt_query")
    private Integer encryptQuery;

    @TableField("replay_protection")
    private Integer replayProtection;

    @TableField("timestamp_skew_seconds")
    private Integer timestampSkewSeconds;

    @TableField("include_paths_json")
    private String includePathsJson;

    @TableField("exclude_paths_json")
    private String excludePathsJson;

    @TableField("sensitive_paths_json")
    private String sensitivePathsJson;

    @TableField("policy_version")
    private Long policyVersion;

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getScopeMode() {
        return scopeMode;
    }

    public void setScopeMode(String scopeMode) {
        this.scopeMode = scopeMode;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Integer getEncryptRequest() {
        return encryptRequest;
    }

    public void setEncryptRequest(Integer encryptRequest) {
        this.encryptRequest = encryptRequest;
    }

    public Integer getEncryptResponse() {
        return encryptResponse;
    }

    public void setEncryptResponse(Integer encryptResponse) {
        this.encryptResponse = encryptResponse;
    }

    public Integer getEncryptQuery() {
        return encryptQuery;
    }

    public void setEncryptQuery(Integer encryptQuery) {
        this.encryptQuery = encryptQuery;
    }

    public Integer getReplayProtection() {
        return replayProtection;
    }

    public void setReplayProtection(Integer replayProtection) {
        this.replayProtection = replayProtection;
    }

    public Integer getTimestampSkewSeconds() {
        return timestampSkewSeconds;
    }

    public void setTimestampSkewSeconds(Integer timestampSkewSeconds) {
        this.timestampSkewSeconds = timestampSkewSeconds;
    }

    public String getIncludePathsJson() {
        return includePathsJson;
    }

    public void setIncludePathsJson(String includePathsJson) {
        this.includePathsJson = includePathsJson;
    }

    public String getExcludePathsJson() {
        return excludePathsJson;
    }

    public void setExcludePathsJson(String excludePathsJson) {
        this.excludePathsJson = excludePathsJson;
    }

    public String getSensitivePathsJson() {
        return sensitivePathsJson;
    }

    public void setSensitivePathsJson(String sensitivePathsJson) {
        this.sensitivePathsJson = sensitivePathsJson;
    }

    public Long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Long policyVersion) {
        this.policyVersion = policyVersion;
    }
}
