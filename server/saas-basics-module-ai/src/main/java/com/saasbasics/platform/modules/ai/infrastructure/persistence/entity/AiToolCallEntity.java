package com.saasbasics.platform.modules.ai.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.saasbasics.platform.common.model.BaseTenantEntity;
import java.time.LocalDateTime;

@TableName("ai_tool_call")
public class AiToolCallEntity extends BaseTenantEntity {

    private Long runId;
    private String callNo;
    private String toolCode;
    private String adapterType;
    private String riskLevel;
    private String permissionCode;
    private String idempotencyKey;
    private String argumentsSummary;
    private String resultSummary;
    private Integer permissionGranted;
    private String approvalStatus;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String rejectReason;
    private String executionStatus;
    private Long latencyMs;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getCallNo() { return callNo; }
    public void setCallNo(String callNo) { this.callNo = callNo; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getArgumentsSummary() { return argumentsSummary; }
    public void setArgumentsSummary(String argumentsSummary) { this.argumentsSummary = argumentsSummary; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Integer getPermissionGranted() { return permissionGranted; }
    public void setPermissionGranted(Integer permissionGranted) { this.permissionGranted = permissionGranted; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
}
