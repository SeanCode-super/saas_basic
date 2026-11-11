package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("ai_tool")
public class AiToolEntity extends BaseTenantEntity {

    private String toolCode;
    private String toolName;
    private String semanticVersion;
    private String description;
    private String inputSchemaJson;
    private String outputSchemaJson;
    private String permissionCode;
    private String riskLevel;
    private String adapterType;
    private Long mcpServerId;
    private Integer whitelisted;
    private Integer idempotent;
    private Integer timeoutMs;
    private String status;

    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getSemanticVersion() { return semanticVersion; }
    public void setSemanticVersion(String semanticVersion) { this.semanticVersion = semanticVersion; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public void setOutputSchemaJson(String outputSchemaJson) { this.outputSchemaJson = outputSchemaJson; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
    public Long getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(Long mcpServerId) { this.mcpServerId = mcpServerId; }
    public Integer getWhitelisted() { return whitelisted; }
    public void setWhitelisted(Integer whitelisted) { this.whitelisted = whitelisted; }
    public Integer getIdempotent() { return idempotent; }
    public void setIdempotent(Integer idempotent) { this.idempotent = idempotent; }
    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
