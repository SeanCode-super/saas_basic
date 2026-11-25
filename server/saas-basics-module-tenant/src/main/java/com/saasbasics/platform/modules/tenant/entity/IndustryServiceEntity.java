package com.saasbasics.platform.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("plat_industry_service")
public class IndustryServiceEntity {

    @TableField("app_code")
    private String appCode;

    @TableField("revision")
    private Integer revision;

    @TableField("manifest_json")
    private String manifestJson;

    @TableField("credential_cipher")
    private String credentialCipher;

    @TableField("project_design_json")
    private String projectDesignJson;

    public String getProjectDesignJson() { return projectDesignJson; }
    public void setProjectDesignJson(String value) { projectDesignJson = value; }

    @TableField("created_at")
    private LocalDateTime createdAt;

    public IndustryServiceEntity() {}

    public IndustryServiceEntity(String appCode, Integer revision, String manifestJson, String credentialCipher) {
        this.appCode = appCode;
        this.revision = revision;
        this.manifestJson = manifestJson;
        this.credentialCipher = credentialCipher;
        this.createdAt = LocalDateTime.now();
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public String getCredentialCipher() {
        return credentialCipher;
    }

    public void setCredentialCipher(String credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
