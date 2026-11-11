package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;

@TableName("ai_run_step")
public class AiRunStepEntity extends BaseTenantEntity {

    private Long runId;
    private Integer stepNo;
    private String stepType;
    private String modelId;
    private String toolCode;
    private String status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;
    private String errorType;
    private String detailJson;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
}
