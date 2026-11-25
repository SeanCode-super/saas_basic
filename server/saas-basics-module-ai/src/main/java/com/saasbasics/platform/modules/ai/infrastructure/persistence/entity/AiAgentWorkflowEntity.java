package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("ai_agent_workflow")
public class AiAgentWorkflowEntity extends BaseTenantEntity {

    @TableField("public_id")
    private String publicId;

    @TableField("workflow_code")
    private String workflowCode;

    @TableField("workflow_name")
    private String workflowName;

    @TableField("agent_id")
    private Long agentId;

    @TableField("trigger_mode")
    private String triggerMode;

    @TableField("graph_json")
    private String graphJson;

    @TableField("variables_schema_json")
    private String variablesSchemaJson;

    @TableField("default_model_id")
    private String defaultModelId;

    @TableField("fallback_model_ids_json")
    private String fallbackModelIdsJson;

    @TableField("status")
    private String status;

    @TableField("published_version")
    private Integer publishedVersion;

    @TableField("run_count")
    private Integer runCount;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(String workflowCode) { this.workflowCode = workflowCode; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getTriggerMode() { return triggerMode; }
    public void setTriggerMode(String triggerMode) { this.triggerMode = triggerMode; }
    public String getGraphJson() { return graphJson; }
    public void setGraphJson(String graphJson) { this.graphJson = graphJson; }
    public String getVariablesSchemaJson() { return variablesSchemaJson; }
    public void setVariablesSchemaJson(String variablesSchemaJson) { this.variablesSchemaJson = variablesSchemaJson; }
    public String getDefaultModelId() { return defaultModelId; }
    public void setDefaultModelId(String defaultModelId) { this.defaultModelId = defaultModelId; }
    public String getFallbackModelIdsJson() { return fallbackModelIdsJson; }
    public void setFallbackModelIdsJson(String fallbackModelIdsJson) { this.fallbackModelIdsJson = fallbackModelIdsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(Integer publishedVersion) { this.publishedVersion = publishedVersion; }
    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }
}
