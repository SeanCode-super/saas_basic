package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.math.BigDecimal;

@TableName("ai_agent")
public class AiAgentEntity extends BaseTenantEntity {

    @TableField("agent_code")
    private String agentCode;

    @TableField("agent_name")
    private String agentName;

    @TableField("avatar")
    private String avatar;

    @TableField("role_title")
    private String roleTitle;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("model_id")
    private String modelId;

    @TableField("kb_ids_json")
    private String kbIdsJson;

    @TableField("tool_codes_json")
    private String toolCodesJson;

    @TableField("channel_types")
    private String channelTypes;

    @TableField("temperature")
    private BigDecimal temperature;

    @TableField("welcome_message")
    private String welcomeMessage;

    @TableField("status")
    private String status;

    @TableField("run_count")
    private Integer runCount;

    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRoleTitle() { return roleTitle; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getKbIdsJson() { return kbIdsJson; }
    public void setKbIdsJson(String kbIdsJson) { this.kbIdsJson = kbIdsJson; }
    public String getToolCodesJson() { return toolCodesJson; }
    public void setToolCodesJson(String toolCodesJson) { this.toolCodesJson = toolCodesJson; }
    public String getChannelTypes() { return channelTypes; }
    public void setChannelTypes(String channelTypes) { this.channelTypes = channelTypes; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }
}
